// MARK: - RemoteAPIHandlers+Projects
// Iteration 9 split. Project endpoints (list / get / recent / activate / open)
// plus their path-safety helpers extracted from ``RemoteAPIHandlers`` to keep
// the primary type body under SwiftLint's `type_body_length` budget.
// Behaviour unchanged.
//
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOHTTP1
import OSLog

extension RemoteAPIHandlers {

    // MARK: - Reads

    func handleListProjects(channel: Channel, corsOrigin: String?) {
        let projectResponses = projectManager.projects.map { project in
            buildProjectResponse(
                project: project,
                isActive: project.id == projectManager.activeProjectId
            )
        }
        let resp = ProjectsListResponse(
            projects: projectResponses,
            activeProjectId: projectManager.activeProjectId?.uuidString
        )
        writer.sendEncodableResponse(resp, status: .ok, channel: channel, corsOrigin: corsOrigin)
    }

    func handleGetProject(projectId: UUID, channel: Channel, corsOrigin: String?) {
        guard let project = projectManager.project(for: projectId) else {
            let resp = ErrorResponse(
                error: ErrorDetail(code: "PROJECT_NOT_FOUND", message: "Unknown project ID.")
            )
            writer.sendEncodableResponse(resp, status: .notFound, channel: channel, corsOrigin: corsOrigin)
            return
        }
        let resp = buildProjectResponse(
            project: project,
            isActive: project.id == projectManager.activeProjectId
        )
        writer.sendEncodableResponse(resp, status: .ok, channel: channel, corsOrigin: corsOrigin)
    }

    func handleListRecentProjects(
        channel: Channel,
        corsOrigin: String?,
        isoFormatter: ISO8601DateFormatter
    ) {
        let recentResponses = projectManager.recentProjects.map { project in
            // SECURITY: expose only the directory name, not the full path.
            RecentProjectResponse(
                name: project.name,
                path: project.path.lastPathComponent,
                lastOpened: isoFormatter.string(from: project.lastOpened)
            )
        }
        let resp = RecentProjectsListResponse(projects: recentResponses)
        writer.sendEncodableResponse(resp, status: .ok, channel: channel, corsOrigin: corsOrigin)
    }

    // MARK: - Mutations

    func handleActivateProject(projectId: UUID, channel: Channel, corsOrigin: String?) {
        guard projectManager.project(for: projectId) != nil else {
            let resp = ErrorResponse(
                error: ErrorDetail(code: "PROJECT_NOT_FOUND", message: "Unknown project ID.")
            )
            writer.sendEncodableResponse(
                resp, status: .notFound, channel: channel, corsOrigin: corsOrigin
            )
            return
        }
        projectManager.activeProjectId = projectId
        writer.sendEncodableResponse(
            OKResponse(ok: true), status: .ok, channel: channel, corsOrigin: corsOrigin
        )
    }

    func handleOpenProject(
        body: ByteBuffer?,
        channel: Channel,
        corsOrigin: String?,
        decoder: JSONDecoder
    ) {
        guard let request = Self.decodeOpenProjectRequest(body: body, decoder: decoder) else {
            let resp = ErrorResponse(
                error: ErrorDetail(code: "INVALID_BODY", message: "Expected JSON with 'path' field.")
            )
            writer.sendEncodableResponse(
                resp, status: .badRequest, channel: channel, corsOrigin: corsOrigin
            )
            return
        }

        let pathURL = Self.resolveProjectPath(request.path)

        // SECURITY: reject paths outside the user's home directory.
        guard Self.isWithinHomeDirectory(pathURL) else {
            let resp = ErrorResponse(
                error: ErrorDetail(
                    code: "PATH_FORBIDDEN",
                    message: "Only paths within the user's home directory are allowed."
                )
            )
            writer.sendEncodableResponse(
                resp, status: .forbidden, channel: channel, corsOrigin: corsOrigin
            )
            return
        }

        // Already-open: just activate.
        if let existing = projectManager.project(at: pathURL) {
            projectManager.activeProjectId = existing.id
            let resp = OpenProjectResponse(ok: true, projectId: existing.id.uuidString)
            writer.sendEncodableResponse(resp, status: .ok, channel: channel, corsOrigin: corsOrigin)
            return
        }

        do {
            let project = try projectManager.addProject(at: pathURL)
            projectManager.activeProjectId = project.id
            let resp = OpenProjectResponse(ok: true, projectId: project.id.uuidString)
            // API-3: a brand-new project was just created → 201 Created.
            // The already-open branch above returns 200 (resource pre-existed).
            writer.sendEncodableResponse(resp, status: .created, channel: channel, corsOrigin: corsOrigin)
        } catch {
            // SECURITY (H2): do NOT echo `error.localizedDescription` — POSIX
            // errors expose the user's full path. Log privately, return a
            // generic message.
            Logger.remoteControl.warning(
                "handleOpenProject: addProject failed for path: \(error.localizedDescription, privacy: .private)"
            )
            let resp = ErrorResponse(
                error: ErrorDetail(code: "OPEN_FAILED", message: "Failed to open project.")
            )
            writer.sendEncodableResponse(
                resp, status: .badRequest, channel: channel, corsOrigin: corsOrigin
            )
        }
    }

    // MARK: - Helpers

    /// Decode the `{ "path": ... }` body of an open-project request, or `nil`
    /// when the body is missing / malformed.
    private static func decodeOpenProjectRequest(
        body: ByteBuffer?,
        decoder: JSONDecoder
    ) -> OpenProjectRequest? {
        guard let body,
              let bodyData = body.getBytes(
                at: body.readerIndex, length: body.readableBytes
              ).map({ Data($0) }) else {
            return nil
        }
        return try? decoder.decode(OpenProjectRequest.self, from: bodyData)
    }

    /// SEC-M3: resolve any symlink component before the home-directory
    /// check. `standardizedFileURL` only normalises `.` / `..` and case;
    /// it leaves symlinks intact, so a path like `~/link_to_etc`
    /// (link target `/etc`) would otherwise pass the prefix check yet
    /// resolve to a directory outside the user's home. We compare
    /// realpath-resolved paths on both sides.
    private static func resolveProjectPath(_ rawPath: String) -> URL {
        URL(fileURLWithPath: rawPath)
            .standardizedFileURL
            .resolvingSymlinksInPath()
    }

    /// `true` when `pathURL` (already realpath-resolved) lives within the
    /// current user's home directory.
    private static func isWithinHomeDirectory(_ pathURL: URL) -> Bool {
        let homePath = FileManager.default.homeDirectoryForCurrentUser
            .standardizedFileURL
            .resolvingSymlinksInPath()
            .path
        // SEC-1: exact match OR true subpath. A plain `hasPrefix(homePath)`
        // lets `/Users/johnathan` pass the `/Users/john` check. Requiring a
        // trailing separator on the prefix closes that sibling-directory
        // bypass. `.path` never carries a trailing slash, so appending "/"
        // is safe here.
        return pathURL.path == homePath || pathURL.path.hasPrefix(homePath + "/")
    }

    /// Build a `ProjectResponse` with session info derived from the active
    /// terminal service + bridges.
    ///
    /// P1-7: Uses the O(1) `bridge(forSession:)` index instead of the
    /// previous O(n) `activeBridges.values.first { $0.sessionId == id }`
    /// linear scan.
    private func buildProjectResponse(project: Project, isActive: Bool) -> ProjectResponse {
        let sessions = terminalService.sessions(for: project.id).map { session in
            // Populate optional cost fields for agent sessions (reconnect recovery).
            let costSnap = session.isAgentSession
                ? costTrackerService?.snapshot(for: session.id)
                : nil
            // P1-7: O(1) attachment lookup via secondary index.
            let attachedBridge = serverRef?.bridgeRegistry.bridge(forSession: session.id)
            return SessionResponse(
                id: session.id.uuidString,
                title: session.title,
                state: session.state.remoteAPIString,
                isAgent: session.isAgentSession,
                hasRemoteAttachment: attachedBridge != nil,
                attachedDeviceId: attachedBridge?.deviceId.uuidString,
                totalTokens: costSnap.map { $0.totalTokens > 0 ? $0.totalTokens : nil } ?? nil,
                estimatedCostUsd: costSnap?.costUSD
            )
        }
        // SECURITY: expose only `lastPathComponent`, not full path.
        return ProjectResponse(
            id: project.id.uuidString,
            name: project.name,
            path: project.path.lastPathComponent,
            color: project.color?.value,
            isActive: isActive,
            git: nil,
            sessions: sessions
        )
    }
}

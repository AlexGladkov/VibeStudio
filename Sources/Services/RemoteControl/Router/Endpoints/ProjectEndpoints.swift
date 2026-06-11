// MARK: - ProjectEndpoints
// /api/v1/projects/* handlers.
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOHTTP1

/// Endpoint group for project listing, activation, and opening.
@MainActor
struct ProjectEndpoints {

    let projectManager: any ProjectManaging
    let terminalService: TerminalService
    let builder: HTTPResponseBuilder
    let decoder: JSONDecoder

    // MARK: - GET

    /// GET /api/v1/projects
    func handleListProjects(
        serverRef: RemoteControlServer?,
        corsOrigin: String?,
        allocator: ByteBufferAllocator
    ) -> (HTTPResponseHead, ByteBuffer)? {
        let projectResponses = projectManager.projects.map { project in
            Self.buildProjectResponse(
                project: project,
                isActive: project.id == projectManager.activeProjectId,
                terminalService: terminalService,
                serverRef: serverRef
            )
        }
        let resp = ProjectsListResponse(
            projects: projectResponses,
            activeProjectId: projectManager.activeProjectId?.uuidString
        )
        return builder.encodableResponse(
            status: .ok, value: resp, corsOrigin: corsOrigin, allocator: allocator
        )
    }

    /// GET /api/v1/projects/recent
    func handleListRecentProjects(
        corsOrigin: String?,
        allocator: ByteBufferAllocator
    ) -> (HTTPResponseHead, ByteBuffer)? {
        let isoFormatter = ISO8601DateFormatter()
        let recentResponses = projectManager.recentProjects.map { project in
            // SECURITY: expose only the last path component, never the full path.
            RecentProjectResponse(
                name: project.name,
                path: project.path.lastPathComponent,
                lastOpened: isoFormatter.string(from: project.lastOpened)
            )
        }
        let resp = RecentProjectsListResponse(projects: recentResponses)
        return builder.encodableResponse(
            status: .ok, value: resp, corsOrigin: corsOrigin, allocator: allocator
        )
    }

    /// GET /api/v1/projects/:id
    func handleGetProject(
        projectId: UUID,
        serverRef: RemoteControlServer?,
        corsOrigin: String?,
        allocator: ByteBufferAllocator
    ) -> (HTTPResponseHead, ByteBuffer)? {
        guard let project = projectManager.project(for: projectId) else {
            return builder.errorJSONResponse(
                status: .notFound, code: "PROJECT_NOT_FOUND",
                message: "Unknown project ID.",
                corsOrigin: corsOrigin, allocator: allocator
            )
        }
        let resp = Self.buildProjectResponse(
            project: project,
            isActive: project.id == projectManager.activeProjectId,
            terminalService: terminalService,
            serverRef: serverRef
        )
        return builder.encodableResponse(
            status: .ok, value: resp, corsOrigin: corsOrigin, allocator: allocator
        )
    }

    // MARK: - POST

    /// POST /api/v1/projects/:id/activate
    func handleActivateProject(
        projectId: UUID,
        corsOrigin: String?,
        allocator: ByteBufferAllocator
    ) -> (HTTPResponseHead, ByteBuffer)? {
        guard projectManager.project(for: projectId) != nil else {
            return builder.errorJSONResponse(
                status: .notFound, code: "PROJECT_NOT_FOUND",
                message: "Unknown project ID.",
                corsOrigin: corsOrigin, allocator: allocator
            )
        }
        projectManager.activeProjectId = projectId
        return builder.encodableResponse(
            status: .ok, value: OKResponse(ok: true),
            corsOrigin: corsOrigin, allocator: allocator
        )
    }

    /// POST /api/v1/projects/open
    func handleOpenProject(
        body: ByteBuffer?,
        corsOrigin: String?,
        allocator: ByteBufferAllocator
    ) -> (HTTPResponseHead, ByteBuffer)? {
        guard let body,
              let bodyData = body.getBytes(at: body.readerIndex, length: body.readableBytes).map({ Data($0) }),
              let request = try? decoder.decode(OpenProjectRequest.self, from: bodyData) else {
            return builder.errorJSONResponse(
                status: .badRequest, code: "INVALID_BODY",
                message: "Expected JSON with 'path' field.",
                corsOrigin: corsOrigin, allocator: allocator
            )
        }

        let pathURL = URL(fileURLWithPath: request.path).standardizedFileURL

        // SECURITY: only allow paths within the user's home directory.
        let homePath = FileManager.default.homeDirectoryForCurrentUser.standardizedFileURL.path
        guard pathURL.path.hasPrefix(homePath) else {
            return builder.errorJSONResponse(
                status: .forbidden, code: "PATH_FORBIDDEN",
                message: "Only paths within the user's home directory are allowed.",
                corsOrigin: corsOrigin, allocator: allocator
            )
        }

        if let existing = projectManager.project(at: pathURL) {
            projectManager.activeProjectId = existing.id
            let resp = OpenProjectResponse(ok: true, projectId: existing.id.uuidString)
            return builder.encodableResponse(
                status: .ok, value: resp, corsOrigin: corsOrigin, allocator: allocator
            )
        }

        do {
            let project = try projectManager.addProject(at: pathURL)
            projectManager.activeProjectId = project.id
            let resp = OpenProjectResponse(ok: true, projectId: project.id.uuidString)
            return builder.encodableResponse(
                status: .ok, value: resp, corsOrigin: corsOrigin, allocator: allocator
            )
        } catch {
            return builder.errorJSONResponse(
                status: .badRequest, code: "OPEN_FAILED",
                message: error.localizedDescription,
                corsOrigin: corsOrigin, allocator: allocator
            )
        }
    }

    // MARK: - Response Builder

    /// Build the canonical `ProjectResponse` (used by list + get).
    static func buildProjectResponse(
        project: Project,
        isActive: Bool,
        terminalService: TerminalService,
        serverRef: RemoteControlServer?
    ) -> ProjectResponse {
        let sessions = terminalService.sessions(for: project.id).map { session in
            SessionResponse(
                id: session.id.uuidString,
                title: session.title,
                state: session.state.remoteAPIString,
                isAgent: session.isAgentSession,
                hasRemoteAttachment: serverRef?.activeBridges.values.contains {
                    $0.sessionId == session.id
                } ?? false,
                attachedDeviceId: serverRef?.activeBridges.values.first {
                    $0.sessionId == session.id
                }?.deviceId.uuidString
            )
        }
        // SECURITY: expose only the last path component, never the full path.
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

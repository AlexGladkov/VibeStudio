// MARK: - RemoteAPIHandlers
// ARCH-H1: All `handle*` endpoint logic extracted from HTTPRequestRouter.
// Pure value type pinned to `@MainActor` — no instance state beyond the
// services + writer injected at construction. The router calls into these
// methods after resolving the route + decoding the bearer token.
//
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOHTTP1
import OSLog

/// MainActor-pinned dispatcher that implements every REST endpoint of the
/// Remote Control API.
///
/// **Lifecycle:** One short-lived instance per request (cheap struct copy).
/// All services it holds are reference types or `@MainActor`-bound and are
/// already in scope on the calling event loop.
@MainActor
struct RemoteAPIHandlers {

    // MARK: - Dependencies

    let authService: RemoteAuthService
    let terminalService: TerminalService
    let projectManager: any ProjectManaging
    let preferences: RemoteControlPreferences
    weak var serverRef: RemoteControlServer?
    let writer: HTTPResponseWriter
    let metadata: RemoteServerMetadata

    // MARK: - Auth-free endpoints

    func handleHealth(channel: Channel, corsOrigin: String?) {
        // SECURITY (M1): unauthenticated; do not leak version / uptime / device
        // counts that aid fingerprinting.
        let json = #"{"status":"healthy"}"#
        writer.sendRawJSON(status: .ok, data: Data(json.utf8), channel: channel, corsOrigin: corsOrigin)
    }

    func handleAuthToken(
        head: HTTPRequestHead,
        body: ByteBuffer?,
        channel: Channel,
        clientIP: String,
        remoteAddress: String,
        corsOrigin: String?,
        decoder: JSONDecoder
    ) {
        guard let body,
              let bodyData = body.getBytes(at: body.readerIndex, length: body.readableBytes).map({ Data($0) }),
              let request = try? decoder.decode(AuthTokenRequest.self, from: bodyData) else {
            writer.sendErrorJSON(
                status: .badRequest,
                code: "INVALID_REQUEST",
                message: "Request body must be JSON with a 'pin' field.",
                channel: channel,
                corsOrigin: corsOrigin
            )
            return
        }

        let userAgent = head.headers["User-Agent"].first ?? ""
        let authSvc = authService
        let theWriter = writer
        // ARCH-C1 / SEC-L1: replaced NSLog with privacy-aware os_log.
        Logger.remoteControl.debug(
            "handleAuthToken: clientIP=\(clientIP, privacy: .public) remoteAddr=\(remoteAddress, privacy: .public)"
        )

        Task { @MainActor in
            let result = authSvc.validatePin(request.pin, clientIP: clientIP, userAgent: userAgent)
            RemoteAuditLog.authAttempt(ip: clientIP, success: result.isSuccess)
            Logger.remoteControl.debug(
                "validatePin result=\(result.isSuccess ? "OK" : "FAIL", privacy: .public) ip=\(clientIP, privacy: .public)"
            )

            switch result {
            case .success(let tokenResponse):
                // SECURITY: never log token material.
                Logger.remoteControl.debug("auth success ip=\(clientIP, privacy: .public)")
                let resp = AuthTokenResponseDTO(
                    token: tokenResponse.token,
                    expiresAt: Date().addingTimeInterval(RemoteAuthService.tokenTTL),
                    deviceId: tokenResponse.device.id.uuidString
                )
                theWriter.sendEncodableResponse(
                    resp, status: .ok, channel: channel, corsOrigin: corsOrigin
                )

            case .failure(let error):
                let (status, code, message, retryAfter) = RemoteAuthMiddleware.authErrorResponse(error)
                let resp = ErrorResponse(error: ErrorDetail(code: code, message: message))
                guard let data = try? theWriter.encoder.encode(resp) else { return }
                channel.eventLoop.execute {
                    theWriter.sendRawJSON(
                        status: status,
                        data: data,
                        channel: channel,
                        corsOrigin: corsOrigin,
                        retryAfterSeconds: retryAfter
                    )
                }
            }
        }
    }

    // MARK: - Authenticated endpoints

    func handleAuthValidate(device: RemoteDevice, channel: Channel, corsOrigin: String?) {
        let resp = AuthValidateResponse(
            valid: true,
            deviceId: device.id.uuidString,
            expiresAt: device.connectedAt.addingTimeInterval(RemoteAuthService.tokenTTL)
        )
        writer.sendEncodable(resp, status: .ok, channel: channel, corsOrigin: corsOrigin)
    }

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

    func handleScrollback(
        projectId: UUID,
        sessionId: UUID,
        device: RemoteDevice,
        channel: Channel,
        corsOrigin: String?
    ) {
        // SEC-C3: BOLA prevention. Deny scrollback when another device owns the
        // WS bridge for this session — that buffer may contain that device's
        // secrets. Allow when no bridge exists (initial / reconnect window).
        if let server = serverRef {
            let foreignOwner = server.activeBridges.values.first { bridge in
                bridge.sessionId == sessionId && bridge.deviceId != device.id
            }
            if foreignOwner != nil {
                let resp = ErrorResponse(
                    error: ErrorDetail(
                        code: "SESSION_NOT_OWNED",
                        message: "Session is attached to another device."
                    )
                )
                writer.sendEncodableResponse(
                    resp, status: .forbidden, channel: channel, corsOrigin: corsOrigin
                )
                return
            }
        }

        let fullContent = terminalService.rawScrollbackContent(for: sessionId) ?? ""
        let allLines = fullContent.components(separatedBy: "\n")
        let maxLines = 10_000
        let returnedLines = allLines.suffix(maxLines)
        let content = returnedLines.joined(separator: "\n")
        let resp = ScrollbackResponse(
            content: content,
            totalLines: allLines.count,
            returnedLines: returnedLines.count
        )
        writer.sendEncodableResponse(resp, status: .ok, channel: channel, corsOrigin: corsOrigin)
    }

    func handleListDevices(device: RemoteDevice, channel: Channel, corsOrigin: String?) {
        let deviceResponses = authService.connectedDevices.map { dev in
            DeviceResponse(
                deviceId: dev.id.uuidString,
                ip: dev.ipAddress,
                userAgent: dev.displayName,
                connectedSince: dev.connectedAt,
                lastActivity: dev.connectedAt,
                attachedSessions: [],
                isSelf: dev.id == device.id
            )
        }
        let resp = DevicesListResponse(
            devices: deviceResponses,
            maxDevices: RemoteAuthService.maxDevices
        )
        writer.sendEncodableResponse(resp, status: .ok, channel: channel, corsOrigin: corsOrigin)
    }

    func handleDisconnectDevice(
        deviceId: UUID,
        requestingDevice: RemoteDevice,
        channel: Channel,
        corsOrigin: String?
    ) {
        // SECURITY: a device can only disconnect itself.
        guard deviceId == requestingDevice.id else {
            let resp = ErrorResponse(
                error: ErrorDetail(code: "FORBIDDEN", message: "You can only disconnect your own device.")
            )
            writer.sendEncodableResponse(
                resp, status: .forbidden, channel: channel, corsOrigin: corsOrigin
            )
            return
        }
        serverRef?.disconnect(deviceId)
        let theWriter = writer
        channel.eventLoop.execute {
            theWriter.sendEmptyResponse(status: .noContent, channel: channel, corsOrigin: corsOrigin)
        }
    }

    func handleStatus(channel: Channel, corsOrigin: String?) {
        let resp = StatusResponse(
            server: .init(
                version: metadata.appVersion,
                apiVersion: "1.0.0",
                uptimeSeconds: serverRef?.uptimeSeconds ?? 0,
                port: preferences.remoteControlPort,
                tls: "self-signed",
                bonjourPublished: preferences.bonjourEnabled
            ),
            connections: .init(
                connectedDevices: authService.connectedDevices.count,
                maxDevices: RemoteAuthService.maxDevices,
                activeWebsockets: serverRef?.activeBridges.count ?? 0
            ),
            theme: .init(
                appearance: "dark",
                terminalColors: TerminalColorsResponse(
                    foreground: "#D4D4D4",
                    background: "#1E1E1E",
                    cursor: "#AEAFAD",
                    selection: "#264F78",
                    ansi: [
                        "#000000", "#CD3131", "#0DBC79", "#E5E510",
                        "#2472C8", "#BC3FBC", "#11A8CD", "#E5E5E5",
                        "#666666", "#F14C4C", "#23D18B", "#F5F543",
                        "#3B8EEA", "#D670D6", "#29B8DB", "#FFFFFF"
                    ]
                )
            )
        )
        writer.sendEncodableResponse(resp, status: .ok, channel: channel, corsOrigin: corsOrigin)
    }

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
        guard let body,
              let bodyData = body.getBytes(at: body.readerIndex, length: body.readableBytes).map({ Data($0) }),
              let request = try? decoder.decode(OpenProjectRequest.self, from: bodyData) else {
            let resp = ErrorResponse(
                error: ErrorDetail(code: "INVALID_BODY", message: "Expected JSON with 'path' field.")
            )
            writer.sendEncodableResponse(
                resp, status: .badRequest, channel: channel, corsOrigin: corsOrigin
            )
            return
        }

        // SEC-M3: resolve any symlink component before the home-directory
        // check. `standardizedFileURL` only normalises `.` / `..` and case;
        // it leaves symlinks intact, so a path like `~/link_to_etc`
        // (link target `/etc`) would otherwise pass the prefix check yet
        // resolve to a directory outside the user's home. We compare
        // realpath-resolved paths on both sides.
        let pathURL = URL(fileURLWithPath: request.path)
            .standardizedFileURL
            .resolvingSymlinksInPath()

        // SECURITY: reject paths outside the user's home directory.
        let homePath = FileManager.default.homeDirectoryForCurrentUser
            .standardizedFileURL
            .resolvingSymlinksInPath()
            .path
        guard pathURL.path.hasPrefix(homePath) else {
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
            writer.sendEncodableResponse(resp, status: .ok, channel: channel, corsOrigin: corsOrigin)
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

    func handleAssistantStart(
        body: ByteBuffer?,
        channel: Channel,
        corsOrigin: String?,
        decoder: JSONDecoder
    ) {
        guard let projectId = projectManager.activeProjectId else {
            let resp = ErrorResponse(
                error: ErrorDetail(code: "NO_ACTIVE_PROJECT", message: "No active project is set.")
            )
            writer.sendEncodableResponse(
                resp, status: .badRequest, channel: channel, corsOrigin: corsOrigin
            )
            return
        }

        // Parse optional `assistant` field; default to .claude.
        let requestedAssistant: AIAssistant? = {
            guard let buf = body,
                  let bytes = buf.getBytes(at: buf.readerIndex, length: buf.readableBytes),
                  let parsed = try? decoder.decode(AssistantStartRequest.self, from: Data(bytes)) else {
                return nil
            }
            return AIAssistant(rawValue: parsed.assistant ?? "")
        }()
        let agent = requestedAssistant ?? .claude

        guard let shellSession = terminalService.sessions(for: projectId)
                .first(where: { !$0.isAgentSession }) else {
            let resp = ErrorResponse(
                error: ErrorDetail(
                    code: "NO_SHELL_SESSION",
                    message: "No shell session available for the active project."
                )
            )
            writer.sendEncodableResponse(
                resp, status: .serviceUnavailable, channel: channel, corsOrigin: corsOrigin
            )
            return
        }

        // SEC-C2: Remote Control clients never receive --dangerously-skip-permissions
        // regardless of the local preference. The flag is intentional only for direct
        // local invocation — over-the-wire callers (incl. ngrok) must go through
        // Claude's standard permission prompts.
        let command = agent.launchCommand(skipPermissions: false)
        terminalService.sendInput(command, to: shellSession.id)

        let resp = AssistantStartResponse(
            ok: true,
            assistant: agent.rawValue,
            sessionId: shellSession.id.uuidString
        )
        writer.sendEncodableResponse(resp, status: .ok, channel: channel, corsOrigin: corsOrigin)
    }

    func handleAssistantStop(channel: Channel, corsOrigin: String?) {
        guard let projectId = projectManager.activeProjectId else {
            let resp = ErrorResponse(
                error: ErrorDetail(code: "NO_ACTIVE_PROJECT", message: "No active project is set.")
            )
            writer.sendEncodableResponse(
                resp, status: .badRequest, channel: channel, corsOrigin: corsOrigin
            )
            return
        }

        let sessions = terminalService.sessions(for: projectId)
        guard let targetSession = sessions.first(where: { $0.isAgentSession }) ?? sessions.first else {
            let resp = ErrorResponse(
                error: ErrorDetail(
                    code: "NO_SESSION",
                    message: "No terminal session found for the active project."
                )
            )
            writer.sendEncodableResponse(
                resp, status: .serviceUnavailable, channel: channel, corsOrigin: corsOrigin
            )
            return
        }

        // We cannot know which assistant is running server-side without extra
        // state tracking; default to the universal Ctrl+C interrupt.
        terminalService.sendInput("\u{03}", to: targetSession.id)
        writer.sendEncodableResponse(
            OKResponse(ok: true), status: .ok, channel: channel, corsOrigin: corsOrigin
        )
    }

    // MARK: - Helpers

    /// Build a `ProjectResponse` with session info derived from the active
    /// terminal service + bridges.
    private func buildProjectResponse(project: Project, isActive: Bool) -> ProjectResponse {
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

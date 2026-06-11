// MARK: - RouterRegistry
// Authenticated route table and endpoint composition.
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOHTTP1

/// Composes the endpoint groups and dispatches an authenticated request to
/// the matching method. Pure mapping logic — owns no channel, performs no
/// I/O. Returns the `(HTTPResponseHead, ByteBuffer)` pair the caller writes.
///
/// Lives on `@MainActor` because every endpoint group it instantiates reads
/// MainActor-isolated state.
@MainActor
struct RouterRegistry {

    // MARK: - Shared Deps

    let authService: RemoteAuthService
    let terminalService: TerminalService
    let projectManager: any ProjectManaging
    let preferences: RemoteControlPreferences
    let claudePermissions: any ClaudePermissionsResolving
    let uploadStore: RemoteUploadStore
    let builder: HTTPResponseBuilder
    let decoder: JSONDecoder

    // MARK: - Dispatch

    /// Dispatch an authenticated request to the matching endpoint method.
    ///
    /// Returns `nil` when the matched endpoint dropped silently (JSON encode
    /// failed). The caller treats `nil` as "do not write a response", matching
    /// the original router's behaviour.
    func dispatch(
        path: String,
        method: HTTPMethod,
        body: ByteBuffer?,
        device: RemoteDevice,
        token: String,
        clientIP: String,
        contentType: String = "",
        serverRef: RemoteControlServer?,
        corsOrigin: String?,
        allocator: ByteBufferAllocator
    ) -> (HTTPResponseHead, ByteBuffer)? {

        let auth = AuthEndpoints(authService: authService, builder: builder, decoder: decoder)
        let uploads = UploadEndpoints(store: uploadStore, builder: builder)
        let projects = ProjectEndpoints(
            projectManager: projectManager, terminalService: terminalService,
            builder: builder, decoder: decoder
        )
        let sessions = SessionEndpoints(terminalService: terminalService, builder: builder)
        let agents = AgentEndpoints(
            projectManager: projectManager, terminalService: terminalService,
            claudePermissions: claudePermissions, builder: builder, decoder: decoder
        )
        let diagnostics = DiagnosticsEndpoints(authService: authService, builder: builder)
        let terminals = TerminalEndpoints(builder: builder)

        // GET /api/v1/auth/validate
        if method == .GET && path == "/api/v1/auth/validate" {
            return auth.handleTokenValidate(
                device: device, token: token, corsOrigin: corsOrigin, allocator: allocator
            )
        }

        // POST /api/v1/auth/refresh
        if method == .POST && path == "/api/v1/auth/refresh" {
            return auth.handleTokenRefresh(
                token: token, clientIP: clientIP,
                corsOrigin: corsOrigin, allocator: allocator
            )
        }

        // POST /api/v1/uploads/image
        if method == .POST && path == "/api/v1/uploads/image" {
            return uploads.handleImageUpload(
                body: body, contentType: contentType,
                corsOrigin: corsOrigin, allocator: allocator
            )
        }

        // GET /api/v1/projects
        if method == .GET && path == "/api/v1/projects" {
            return projects.handleListProjects(serverRef: serverRef, corsOrigin: corsOrigin, allocator: allocator)
        }

        // GET /api/v1/projects/recent (must precede :id pattern)
        if method == .GET && path == "/api/v1/projects/recent" {
            return projects.handleListRecentProjects(corsOrigin: corsOrigin, allocator: allocator)
        }

        // GET /api/v1/projects/:id
        if method == .GET,
           let projectId = Self.extractUUID(from: path, pattern: "/api/v1/projects/"),
           !path.contains("/sessions/") {
            return projects.handleGetProject(
                projectId: projectId, serverRef: serverRef,
                corsOrigin: corsOrigin, allocator: allocator
            )
        }

        // GET /api/v1/projects/:pid/sessions/:sid/scrollback
        if method == .GET && path.hasSuffix("/scrollback"),
           let (projectId, sessionId) = Self.extractProjectSession(from: path) {
            return sessions.handleScrollback(
                projectId: projectId, sessionId: sessionId,
                device: device, corsOrigin: corsOrigin, allocator: allocator
            )
        }

        // GET /api/v1/devices
        if method == .GET && path == "/api/v1/devices" {
            return auth.handleListDevices(
                requestingDevice: device, corsOrigin: corsOrigin, allocator: allocator
            )
        }

        // DELETE /api/v1/devices/:id
        if method == .DELETE,
           let deviceId = Self.extractUUID(from: path, pattern: "/api/v1/devices/") {
            return auth.handleRevokeDevice(
                deviceId: deviceId, requestingDevice: device,
                serverRef: serverRef, corsOrigin: corsOrigin, allocator: allocator
            )
        }

        // GET /api/v1/status
        if method == .GET && path == "/api/v1/status" {
            return diagnostics.handleStatus(
                serverRef: serverRef, preferences: preferences,
                corsOrigin: corsOrigin, allocator: allocator
            )
        }

        // POST /api/v1/projects/:id/activate
        if method == .POST,
           let projectId = Self.extractUUID(from: path, pattern: "/api/v1/projects/"),
           path.hasSuffix("/activate") {
            return projects.handleActivateProject(
                projectId: projectId, corsOrigin: corsOrigin, allocator: allocator
            )
        }

        // POST /api/v1/projects/open
        if method == .POST && path == "/api/v1/projects/open" {
            return projects.handleOpenProject(
                body: body, corsOrigin: corsOrigin, allocator: allocator
            )
        }

        // POST /api/v1/assistant/start
        if method == .POST && path == "/api/v1/assistant/start" {
            return agents.handleAssistantStart(
                body: body, corsOrigin: corsOrigin, allocator: allocator
            )
        }

        // POST /api/v1/assistant/stop
        if method == .POST && path == "/api/v1/assistant/stop" {
            return agents.handleAssistantStop(
                corsOrigin: corsOrigin, allocator: allocator
            )
        }

        // Terminal WS endpoints reach here when the client forgot the Upgrade header.
        if method == .GET && (path.hasPrefix("/api/v1/terminal/") || path.hasPrefix("/ws/terminal/")) {
            return terminals.handleUpgradeRequired(corsOrigin: corsOrigin, allocator: allocator)
        }

        return builder.errorJSONResponse(
            status: .notFound, code: "NOT_FOUND",
            message: "The requested resource was not found.",
            corsOrigin: corsOrigin, allocator: allocator
        )
    }

    // MARK: - Path Parsing

    /// Extract a trailing UUID after `pattern` (consumed up to the next `/`).
    static func extractUUID(from path: String, pattern: String) -> UUID? {
        guard path.hasPrefix(pattern) else { return nil }
        let remaining = String(path.dropFirst(pattern.count))
        let idString = remaining.split(separator: "/").first.map(String.init) ?? remaining
        return UUID(uuidString: idString)
    }

    /// Extract both project and session IDs from
    /// `/api/v1/projects/{pid}/sessions/{sid}/scrollback`.
    static func extractProjectSession(from path: String) -> (UUID, UUID)? {
        let parts = path.split(separator: "/")
        guard parts.count >= 7,
              let pidIndex = parts.firstIndex(of: "projects"),
              let sidIndex = parts.firstIndex(of: "sessions"),
              pidIndex + 1 < parts.endIndex,
              sidIndex + 1 < parts.endIndex,
              let pid = UUID(uuidString: String(parts[pidIndex + 1])),
              let sid = UUID(uuidString: String(parts[sidIndex + 1])) else {
            return nil
        }
        return (pid, sid)
    }
}

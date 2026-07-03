// MARK: - RemoteAPIRouter
// ARCH-H1: Pure route → handler dispatcher. No NIO ChannelHandler
// inheritance and no per-channel state — the dispatcher just decides which
// `RemoteAPIHandlers` method to invoke for a given decoded (path, method)
// pair and forwards the auth-resolved device. All response writing goes
// through `HTTPResponseWriter` which the caller wires in.
//
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOHTTP1

/// Stateless dispatcher for authenticated Remote Control API routes.
///
/// Public methods are `@MainActor` because every endpoint touches one of
/// the MainActor-bound services. The dispatcher itself owns no state.
@MainActor
struct RemoteAPIRouter {

    /// Endpoint handlers (services + writer + metadata wired in).
    let handlers: RemoteAPIHandlers

    /// Shared JSON decoder for request body parsing.
    let decoder: JSONDecoder

    /// Shared ISO-8601 formatter for recent-project payloads.
    let isoFormatter: ISO8601DateFormatter

    // MARK: - Authenticated dispatch

    /// Dispatch an authenticated request to its handler.
    ///
    /// - Parameters:
    ///   - request: Fully-parsed request (head, body, path, method, channel,
    ///     CORS origin).
    ///   - device: Auth-resolved caller.
    ///
    /// All decisions are made up-front (sequence of `if` checks) so the
    /// resulting code is trivially reviewable for "is this endpoint
    /// gated by auth?".
    func routeAuthenticated(_ request: ParsedHTTPRequest, device: RemoteDevice) {
        // Ordered fan-out. Groups are contiguous slices of the original
        // if-ladder, so evaluation order is byte-for-byte preserved; each
        // group returns `true` once it has claimed and answered the request.
        if routeProjectReadRequests(request, device: device) { return }
        if routeDeviceReadRequests(request, device: device) { return }
        if routeMutationRequests(request, device: device) { return }

        let channel = request.channel
        let corsOrigin = request.corsOrigin

        // Terminal WS endpoints are upgraded before auth. Reaching this
        // branch means the Upgrade header was missing.
        if request.method == .GET && request.path.hasPrefix("/api/v1/terminal/") {
            handlers.writer.sendErrorJSON(
                status: .badRequest,
                code: "INVALID_REQUEST",
                message: "WebSocket upgrade required for terminal endpoints.",
                channel: channel,
                corsOrigin: corsOrigin
            )
            return
        }

        handlers.writer.sendErrorJSON(
            status: .notFound,
            code: "NOT_FOUND",
            message: "The requested resource was not found.",
            channel: channel,
            corsOrigin: corsOrigin
        )
    }

    /// Project-scoped GET reads (auth/validate, projects list/recent/:id,
    /// scrollback). Contiguous head of the original read if-ladder, so the
    /// `recent`-before-`:id` ordering is preserved exactly. Returns `true`
    /// when a route matched and responded.
    private func routeProjectReadRequests(_ request: ParsedHTTPRequest, device: RemoteDevice) -> Bool {
        let path = request.path
        let method = request.method
        let channel = request.channel
        let corsOrigin = request.corsOrigin

        // GET /api/v1/auth/validate
        if method == .GET && path == "/api/v1/auth/validate" {
            handlers.handleAuthValidate(device: device, channel: channel, corsOrigin: corsOrigin)
            return true
        }
        // GET /api/v1/projects
        if method == .GET && path == "/api/v1/projects" {
            handlers.handleListProjects(channel: channel, corsOrigin: corsOrigin)
            return true
        }
        // GET /api/v1/projects/recent (must precede the :id pattern)
        if method == .GET && path == "/api/v1/projects/recent" {
            handlers.handleListRecentProjects(
                channel: channel, corsOrigin: corsOrigin, isoFormatter: isoFormatter
            )
            return true
        }
        // GET /api/v1/projects/:id
        if method == .GET,
           let projectId = extractUUID(from: path, pattern: "/api/v1/projects/"),
           !path.contains("/sessions/") {
            handlers.handleGetProject(
                projectId: projectId, channel: channel, corsOrigin: corsOrigin
            )
            return true
        }
        // GET /api/v1/projects/:pid/sessions/:sid/scrollback
        if method == .GET && path.hasSuffix("/scrollback"),
           let (projectId, sessionId) = extractProjectSession(from: path) {
            handlers.handleScrollback(
                projectId: projectId,
                sessionId: sessionId,
                device: device,
                channel: channel,
                corsOrigin: corsOrigin
            )
            return true
        }
        return false
    }

    /// Device + status GET/DELETE reads. Contiguous tail of the original read
    /// if-ladder (evaluated only after the project reads miss). Returns `true`
    /// when a route matched and responded.
    private func routeDeviceReadRequests(_ request: ParsedHTTPRequest, device: RemoteDevice) -> Bool {
        let path = request.path
        let method = request.method
        let channel = request.channel
        let corsOrigin = request.corsOrigin

        // GET /api/v1/devices
        if method == .GET && path == "/api/v1/devices" {
            handlers.handleListDevices(
                device: device, channel: channel, corsOrigin: corsOrigin
            )
            return true
        }
        // DELETE /api/v1/devices/:id
        if method == .DELETE,
           let deviceId = extractUUID(from: path, pattern: "/api/v1/devices/") {
            handlers.handleDisconnectDevice(
                deviceId: deviceId,
                requestingDevice: device,
                channel: channel,
                corsOrigin: corsOrigin
            )
            return true
        }
        // GET /api/v1/status
        if method == .GET && path == "/api/v1/status" {
            handlers.handleStatus(channel: channel, corsOrigin: corsOrigin)
            return true
        }
        return false
    }

    /// POST mutations (project activate/open, assistant start/stop). Returns
    /// `true` when a route matched and responded.
    private func routeMutationRequests(_ request: ParsedHTTPRequest, device: RemoteDevice) -> Bool {
        let path = request.path
        let method = request.method
        let body = request.body
        let channel = request.channel
        let corsOrigin = request.corsOrigin

        // POST /api/v1/projects/:id/activate
        if method == .POST,
           let projectId = extractUUID(from: path, pattern: "/api/v1/projects/"),
           path.hasSuffix("/activate") {
            handlers.handleActivateProject(
                projectId: projectId, channel: channel, corsOrigin: corsOrigin
            )
            return true
        }
        // POST /api/v1/projects/open
        if method == .POST && path == "/api/v1/projects/open" {
            handlers.handleOpenProject(
                body: body, channel: channel, corsOrigin: corsOrigin, decoder: decoder
            )
            return true
        }
        // POST /api/v1/assistant/start
        if method == .POST && path == "/api/v1/assistant/start" {
            handlers.handleAssistantStart(
                body: body, channel: channel, corsOrigin: corsOrigin, decoder: decoder
            )
            return true
        }
        // POST /api/v1/assistant/stop
        if method == .POST && path == "/api/v1/assistant/stop" {
            handlers.handleAssistantStop(channel: channel, corsOrigin: corsOrigin)
            return true
        }
        return false
    }

    // MARK: - Path parsing helpers

    /// Extract a UUID component immediately following `pattern` in `path`.
    func extractUUID(from path: String, pattern: String) -> UUID? {
        guard path.hasPrefix(pattern) else { return nil }
        let remaining = String(path.dropFirst(pattern.count))
        let idString = remaining.split(separator: "/").first.map(String.init) ?? remaining
        return UUID(uuidString: idString)
    }

    /// Extract `(projectId, sessionId)` from
    /// `/api/v1/projects/{pid}/sessions/{sid}/scrollback`.
    func extractProjectSession(from path: String) -> (UUID, UUID)? {
        let parts = path.split(separator: "/")
        // Expected: ["api", "v1", "projects", "{pid}", "sessions", "{sid}", "scrollback"]
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

// MARK: - HTTPRequestRouter
// NIO ChannelHandler that routes HTTP requests to API handlers and static files.
// macOS 14+, Swift 5.10

import CryptoKit
import Foundation
import NIOCore
import NIOHTTP1
import NIOWebSocket
import OSLog

/// NIO inbound channel handler that routes HTTP requests to the
/// Remote Control REST API and static file server.
///
/// **Threading model:**
/// This handler runs on a NIO `EventLoop` thread. Service reads that require
/// `@MainActor` (e.g. project lists, scrollback content) use
/// `Task { @MainActor in ... }` with fire-and-forget, then write the response
/// back via `context.eventLoop.execute`.
///
/// `ChannelHandlerContext` is NOT `Sendable` -- it must not be captured in
/// `Task` closures. Instead, cache the `Channel` reference (which IS Sendable).
final class HTTPRequestRouter: ChannelInboundHandler, RemovableChannelHandler {

    typealias InboundIn = HTTPServerRequestPart
    typealias OutboundOut = HTTPServerResponsePart

    // MARK: - Dependencies (thread-safe references)

    /// Auth service -- `@MainActor` but we access it via `Task { @MainActor in }`.
    private let authService: RemoteAuthService

    /// Terminal service -- same pattern.
    private let terminalService: TerminalService

    /// Project manager -- same pattern.
    private let projectManager: any ProjectManaging

    /// Preferences -- same pattern.
    private let preferences: RemoteControlPreferences

    /// Weak reference to the server for bridge registration.
    private weak var serverRef: RemoteControlServer?

    // MARK: - Connection State

    /// Real TCP-level remote address captured in `channelActive`.
    /// Never derived from spoofable headers.
    private var remoteAddress: String = "unknown"

    #if DEBUG
    /// Debug log for WS upgrade attempts (accessible via /api/v1/debug/wslog).
    private static let wsLogLock = NSLock()
    private static var _wsDebugLog: [String] = []
    static var wsDebugLog: [String] {
        wsLogLock.lock()
        defer { wsLogLock.unlock() }
        return _wsDebugLog
    }
    static func wsLog(_ msg: String) {
        let ts = ISO8601DateFormatter().string(from: Date())
        wsLogLock.lock()
        _wsDebugLog.append("[\(ts)] \(msg)")
        if _wsDebugLog.count > 100 { _wsDebugLog.removeFirst() }
        wsLogLock.unlock()
    }
    #endif

    // MARK: - CORS State

    /// Resolved CORS origin for the current request. Set at the top of
    /// `routeRequest` and consumed by `sendRawJSON`, `sendEmptyResponse`,
    /// and `sendCORSPreflight`. `nil` means no CORS header should be emitted.
    private var corsOrigin: String?

    // MARK: - Request Accumulator

    private var requestHead: HTTPRequestHead?
    private var requestBody: ByteBuffer?

    // MARK: - JSON Encoder/Decoder

    private let encoder: JSONEncoder = {
        let e = JSONEncoder()
        e.dateEncodingStrategy = .iso8601
        return e
    }()

    private let decoder = JSONDecoder()

    // MARK: - Init

    init(
        authService: RemoteAuthService,
        terminalService: TerminalService,
        projectManager: any ProjectManaging,
        preferences: RemoteControlPreferences,
        serverRef: RemoteControlServer?
    ) {
        self.authService = authService
        self.terminalService = terminalService
        self.projectManager = projectManager
        self.preferences = preferences
        self.serverRef = serverRef
    }

    // MARK: - ChannelInboundHandler

    func channelActive(context: ChannelHandlerContext) {
        if let remoteAddr = context.channel.remoteAddress {
            switch remoteAddr {
            case .v4(let addr):
                remoteAddress = addr.host
            case .v6(let addr):
                remoteAddress = addr.host
            default:
                remoteAddress = "unknown"
            }
        }
        context.fireChannelActive()
    }

    func channelRead(context: ChannelHandlerContext, data: NIOAny) {
        let part = unwrapInboundIn(data)

        switch part {
        case .head(let head):
            requestHead = head
            requestBody = context.channel.allocator.buffer(capacity: 0)

        case .body(var body):
            // Reject oversized request bodies (64KB limit for API requests).
            if (requestBody?.readableBytes ?? 0) + body.readableBytes > 65_536 {
                requestHead = nil
                requestBody = nil
                return
            }
            requestBody?.writeBuffer(&body)

        case .end:
            guard let head = requestHead else { return }
            let body = requestBody
            let channel = context.channel
            requestHead = nil
            requestBody = nil
            routeRequest(head: head, body: body, channel: channel)
        }
    }

    func errorCaught(context: ChannelHandlerContext, error: Error) {
        Logger.remoteControl.error("HTTPRequestRouter error: \(error.localizedDescription, privacy: .public)")
        context.close(promise: nil)
    }

    // MARK: - Routing

    private func routeRequest(head: HTTPRequestHead, body: ByteBuffer?, channel: Channel) {
        let path = head.uri.split(separator: "?").first.map(String.init) ?? head.uri
        let method = head.method

        #if DEBUG
        let upgradeHeader = head.headers["Upgrade"].first ?? "none"
        let connHeader = head.headers["Connection"].first ?? "none"
        if upgradeHeader != "none" || path.hasPrefix("/api/v1/terminal") {
            HTTPRequestRouter.wsLog("[ROUTER] \(method.rawValue) \(path) Upgrade=\(upgradeHeader) Connection=\(connHeader) ip=\(self.remoteAddress)")
        }
        #endif

        // WebSocket upgrade — handle before anything else.
        // Resolve the safe CORS origin once per request.
        corsOrigin = allowedOrigin(from: head)

        // CORS preflight.
        if method == .OPTIONS {
            sendCORSPreflight(channel: channel)
            return
        }

        // Static files (no auth required).
        if method == .GET {
            switch path {
            case "/":
                serveStaticFile("index.html", contentType: "text/html", channel: channel)
                return
            case "/app.css":
                serveStaticFile("app.css", contentType: "text/css", channel: channel)
                return
            case "/app.js":
                serveStaticFile("app.js", contentType: "application/javascript", channel: channel)
                return
            case "/favicon.ico":
                serveStaticFile("favicon.ico", contentType: "image/x-icon", channel: channel)
                return
            default:
                if path.hasPrefix("/vendor/") {
                    let fileName = String(path.dropFirst(1)) // "vendor/..."
                    let ext = (fileName as NSString).pathExtension
                    let mime = mimeType(for: ext)
                    serveStaticFile(fileName, contentType: mime, channel: channel)
                    return
                }
            }
        }

        // Health check (no auth required).
        if method == .GET && path == "/api/v1/health" {
            handleHealth(channel: channel)
            return
        }

        // Auth endpoints (no bearer token required).
        if method == .POST && path == "/api/v1/auth/token" {
            handleAuthToken(head: head, body: body, channel: channel)
            return
        }

        #if DEBUG
        // Debug-only: expose current PIN for automated testing.
        if method == .GET && path == "/api/v1/debug/pin" {
            let authSvc = authService
            Task { @MainActor in
                let pin = authSvc.currentPin
                let json = "{\"pin\":\"\(pin)\"}"
                channel.eventLoop.execute {
                    self.sendRawJSON(status: .ok, data: Data(json.utf8), channel: channel)
                }
            }
            return
        }
        // Debug-only: show WS upgrade log.
        if method == .GET && path == "/api/v1/debug/wslog" {
            let log = HTTPRequestRouter.wsDebugLog
            let json = "[" + log.map { "\"\($0.replacingOccurrences(of: "\"", with: "\\\""))\"" }.joined(separator: ",") + "]"
            sendRawJSON(status: .ok, data: Data(json.utf8), channel: channel)
            return
        }
        // Debug-only: return current PIN for testing.
        if method == .GET && path == "/api/v1/debug/pin" {
            let authSvc = authService
            Task { @MainActor in
                let pin = authSvc.currentPin
                let json = "{\"pin\":\"\(pin)\"}"
                channel.eventLoop.execute {
                    self.sendRawJSON(status: .ok, data: Data(json.utf8), channel: channel)
                }
            }
            return
        }
        // Debug-only: show connected devices, tokens, IPs, and caller IP.
        if method == .GET && path == "/api/v1/debug/state" {
            let authSvc = authService
            let callerIP = remoteAddress
            Task { @MainActor in
                let devices = authSvc.connectedDevices.map { d in
                    "{\"id\":\"\(d.id)\",\"ip\":\"\(d.ipAddress)\"}"
                }.joined(separator: ",")
                let deviceCount = authSvc.connectedDevices.count
                let json = "{\"caller_ip\":\"\(callerIP)\",\"devices\":[\(devices)],\"device_count\":\(deviceCount)}"
                channel.eventLoop.execute {
                    self.sendRawJSON(status: .ok, data: Data(json.utf8), channel: channel)
                }
            }
            return
        }
        #endif

        // WebSocket upgrade for terminal endpoints.
        // Handled here (not via NIO's HTTPServerUpgradeHandler) because that handler
        // is one-shot and removes itself after the first non-upgrade request, breaking
        // HTTP/1.1 keep-alive connections where the WS upgrade comes after page load.
        // Browser WebSocket API doesn't support custom headers, so the token is in query params.
        if method == .GET && path.hasPrefix("/api/v1/terminal/") &&
           head.headers["Upgrade"].first?.caseInsensitiveCompare("websocket") == .orderedSame {
            handleWebSocketUpgrade(head: head, path: path, channel: channel)
            return
        }

        // All other /api/ endpoints require authentication.
        if path.hasPrefix("/api/") {
            let clientIP = extractClientIP(from: head)
            guard let token = extractBearerToken(from: head) else {
                sendErrorJSON(
                    status: .unauthorized,
                    code: "AUTH_REQUIRED",
                    message: "Authorization header with Bearer token is required.",
                    channel: channel
                )
                return
            }

            // Validate token on MainActor, then continue routing.
            let authSvc = authService
            let termSvc = terminalService
            let projMgr = projectManager
            let prefs = preferences
            let serverRef = self.serverRef
            let enc = encoder

            Task { @MainActor [weak self] in
                guard let self else { return }
                let result = authSvc.validateToken(token, clientIP: clientIP)
                switch result {
                case .failure(let error):
                    let (status, code, message) = self.authErrorResponse(error)
                    let respData = try? enc.encode(
                        ErrorResponse(error: ErrorDetail(code: code, message: message))
                    )
                    channel.eventLoop.execute { [weak self] in
                        self?.sendRawJSON(
                            status: status,
                            data: respData ?? Data(),
                            channel: channel
                        )
                    }

                case .success(let device):
                    // Route authenticated request.
                    self.routeAuthenticated(
                        path: path,
                        method: method,
                        head: head,
                        body: body,
                        device: device,
                        channel: channel,
                        terminalService: termSvc,
                        projectManager: projMgr,
                        preferences: prefs,
                        serverRef: serverRef,
                        authService: authSvc,
                        encoder: enc
                    )
                }
            }
            return
        }

        // Unknown path.
        sendErrorJSON(
            status: .notFound,
            code: "NOT_FOUND",
            message: "The requested resource was not found.",
            channel: channel
        )
    }

    // MARK: - Authenticated Route Dispatch

    /// Route an authenticated API request. Runs on MainActor.
    @MainActor
    private func routeAuthenticated(
        path: String,
        method: HTTPMethod,
        head: HTTPRequestHead,
        body: ByteBuffer?,
        device: RemoteDevice,
        channel: Channel,
        terminalService: TerminalService,
        projectManager: any ProjectManaging,
        preferences: RemoteControlPreferences,
        serverRef: RemoteControlServer?,
        authService: RemoteAuthService,
        encoder: JSONEncoder
    ) {
        // GET /api/v1/auth/validate
        if method == .GET && path == "/api/v1/auth/validate" {
            let resp = AuthValidateResponse(
                valid: true,
                deviceId: device.id.uuidString,
                expiresAt: device.connectedAt.addingTimeInterval(4 * 60 * 60)
            )
            sendEncodable(resp, channel: channel, encoder: encoder)
            return
        }

        // GET /api/v1/projects
        if method == .GET && path == "/api/v1/projects" {
            handleListProjects(
                projectManager: projectManager,
                terminalService: terminalService,
                serverRef: serverRef,
                channel: channel,
                encoder: encoder
            )
            return
        }

        // GET /api/v1/projects/:id
        if method == .GET, let projectId = extractUUID(from: path, pattern: "/api/v1/projects/"),
           !path.contains("/sessions/") {
            handleGetProject(
                projectId: projectId,
                projectManager: projectManager,
                terminalService: terminalService,
                serverRef: serverRef,
                channel: channel,
                encoder: encoder
            )
            return
        }

        // GET /api/v1/projects/:pid/sessions/:sid/scrollback
        if method == .GET && path.hasSuffix("/scrollback"),
           let (projectId, sessionId) = extractProjectSession(from: path) {
            handleScrollback(
                projectId: projectId,
                sessionId: sessionId,
                device: device,
                serverRef: serverRef,
                terminalService: terminalService,
                channel: channel,
                encoder: encoder
            )
            return
        }

        // GET /api/v1/devices
        if method == .GET && path == "/api/v1/devices" {
            handleListDevices(
                authService: authService,
                device: device,
                channel: channel,
                encoder: encoder
            )
            return
        }

        // DELETE /api/v1/devices/:id
        if method == .DELETE,
           let deviceId = extractUUID(from: path, pattern: "/api/v1/devices/") {
            handleDisconnectDevice(
                deviceId: deviceId,
                serverRef: serverRef,
                channel: channel
            )
            return
        }

        // GET /api/v1/status
        if method == .GET && path == "/api/v1/status" {
            handleStatus(
                serverRef: serverRef,
                preferences: preferences,
                authService: authService,
                channel: channel,
                encoder: encoder
            )
            return
        }

        // POST /api/v1/projects/:id/activate
        if method == .POST,
           let projectId = extractUUID(from: path, pattern: "/api/v1/projects/"),
           path.hasSuffix("/activate") {
            handleActivateProject(
                projectId: projectId,
                projectManager: projectManager,
                channel: channel,
                encoder: encoder
            )
            return
        }

        // POST /api/v1/assistant/start
        if method == .POST && path == "/api/v1/assistant/start" {
            handleAssistantStart(
                body: body,
                projectManager: projectManager,
                terminalService: terminalService,
                channel: channel,
                encoder: encoder
            )
            return
        }

        // POST /api/v1/assistant/stop
        if method == .POST && path == "/api/v1/assistant/stop" {
            handleAssistantStop(
                projectManager: projectManager,
                terminalService: terminalService,
                channel: channel,
                encoder: encoder
            )
            return
        }

        // Terminal WS endpoints are handled before auth in routeRequest
        // (handleWebSocketUpgrade). If we reach here, the request is missing
        // the Upgrade header.
        if method == .GET && path.hasPrefix("/api/v1/terminal/") {
            sendErrorJSON(
                status: .badRequest,
                code: "INVALID_REQUEST",
                message: "WebSocket upgrade required for terminal endpoints.",
                channel: channel
            )
            return
        }

        sendErrorJSON(
            status: .notFound,
            code: "NOT_FOUND",
            message: "The requested resource was not found.",
            channel: channel
        )
    }

    // MARK: - Endpoint Handlers

    private func handleHealth(channel: Channel) {
        let resp = ["status": "healthy"]
        guard let data = try? encoder.encode(resp) else { return }
        sendRawJSON(status: .ok, data: data, channel: channel)
    }

    private func handleAuthToken(head: HTTPRequestHead, body: ByteBuffer?, channel: Channel) {
        guard let body,
              let bodyData = body.getBytes(at: body.readerIndex, length: body.readableBytes).map({ Data($0) }),
              let request = try? decoder.decode(AuthTokenRequest.self, from: bodyData) else {
            sendErrorJSON(
                status: .badRequest,
                code: "INVALID_REQUEST",
                message: "Request body must be JSON with a 'pin' field.",
                channel: channel
            )
            return
        }

        let clientIP = extractClientIP(from: head)
        let userAgent = head.headers["User-Agent"].first ?? ""
        let authSvc = authService
        let enc = encoder
        #if DEBUG
        NSLog("[RC-AUTH] handleAuthToken: clientIP=\(clientIP) remoteAddr=\(remoteAddress)")
        #endif

        Task { @MainActor [weak self] in
            guard let self else { return }
            let result = authSvc.validatePin(request.pin, clientIP: clientIP, userAgent: userAgent)
            RemoteAuditLog.authAttempt(ip: clientIP, success: result.isSuccess)
            #if DEBUG
            NSLog("[RC-AUTH] validatePin result: \(result.isSuccess ? "OK" : "FAIL") ip=\(clientIP)")
            #endif

            switch result {
            case .success(let tokenResponse):
                #if DEBUG
                NSLog("[RC-AUTH] issued token=\(String(tokenResponse.token.prefix(8)))...")
                #endif
                let resp = AuthTokenResponseDTO(
                    token: tokenResponse.token,
                    expiresAt: Date().addingTimeInterval(4 * 60 * 60),
                    deviceId: tokenResponse.device.id.uuidString
                )
                if let data = try? enc.encode(resp) {
                    channel.eventLoop.execute { [weak self] in
                        self?.sendRawJSON(status: .ok, data: data, channel: channel)
                    }
                }

            case .failure(let error):
                let (status, code, message) = self.authErrorResponse(error)
                let resp = ErrorResponse(error: ErrorDetail(code: code, message: message))
                if let data = try? enc.encode(resp) {
                    channel.eventLoop.execute { [weak self] in
                        self?.sendRawJSON(status: status, data: data, channel: channel)
                    }
                }
            }
        }
    }

    @MainActor
    private func handleListProjects(
        projectManager: any ProjectManaging,
        terminalService: TerminalService,
        serverRef: RemoteControlServer?,
        channel: Channel,
        encoder: JSONEncoder
    ) {
        let projectResponses = projectManager.projects.map { project in
            buildProjectResponse(
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
        if let data = try? encoder.encode(resp) {
            channel.eventLoop.execute { [weak self] in
                self?.sendRawJSON(status: .ok, data: data, channel: channel)
            }
        }
    }

    @MainActor
    private func handleGetProject(
        projectId: UUID,
        projectManager: any ProjectManaging,
        terminalService: TerminalService,
        serverRef: RemoteControlServer?,
        channel: Channel,
        encoder: JSONEncoder
    ) {
        guard let project = projectManager.project(for: projectId) else {
            let resp = ErrorResponse(
                error: ErrorDetail(code: "PROJECT_NOT_FOUND", message: "Unknown project ID.")
            )
            if let data = try? encoder.encode(resp) {
                channel.eventLoop.execute { [weak self] in
                    self?.sendRawJSON(status: .notFound, data: data, channel: channel)
                }
            }
            return
        }
        let resp = buildProjectResponse(
            project: project,
            isActive: project.id == projectManager.activeProjectId,
            terminalService: terminalService,
            serverRef: serverRef
        )
        if let data = try? encoder.encode(resp) {
            channel.eventLoop.execute { [weak self] in
                self?.sendRawJSON(status: .ok, data: data, channel: channel)
            }
        }
    }

    @MainActor
    private func handleScrollback(
        projectId: UUID,
        sessionId: UUID,
        device: RemoteDevice,
        serverRef: RemoteControlServer?,
        terminalService: TerminalService,
        channel: Channel,
        encoder: JSONEncoder
    ) {
        // Only allow scrollback access for sessions this device is attached to.
        let isAttached = serverRef?.activeBridges[device.id]?.sessionId == sessionId
        guard isAttached else {
            let resp = ErrorResponse(
                error: ErrorDetail(
                    code: "SESSION_NOT_ATTACHED",
                    message: "You can only read scrollback for sessions you are attached to."
                )
            )
            if let data = try? encoder.encode(resp) {
                channel.eventLoop.execute { [weak self] in
                    self?.sendRawJSON(status: .forbidden, data: data, channel: channel)
                }
            }
            return
        }

        let content = terminalService.rawScrollbackContent(for: sessionId) ?? ""
        let lines = content.components(separatedBy: "\n")
        let resp = ScrollbackResponse(
            content: content,
            totalLines: lines.count,
            returnedLines: lines.count
        )
        if let data = try? encoder.encode(resp) {
            channel.eventLoop.execute { [weak self] in
                self?.sendRawJSON(status: .ok, data: data, channel: channel)
            }
        }
    }

    @MainActor
    private func handleListDevices(
        authService: RemoteAuthService,
        device: RemoteDevice,
        channel: Channel,
        encoder: JSONEncoder
    ) {
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
        let resp = DevicesListResponse(devices: deviceResponses, maxDevices: 3)
        if let data = try? encoder.encode(resp) {
            channel.eventLoop.execute { [weak self] in
                self?.sendRawJSON(status: .ok, data: data, channel: channel)
            }
        }
    }

    @MainActor
    private func handleDisconnectDevice(
        deviceId: UUID,
        serverRef: RemoteControlServer?,
        channel: Channel
    ) {
        serverRef?.disconnect(deviceId)
        channel.eventLoop.execute { [weak self] in
            self?.sendEmptyResponse(status: .noContent, channel: channel)
        }
    }

    @MainActor
    private func handleStatus(
        serverRef: RemoteControlServer?,
        preferences: RemoteControlPreferences,
        authService: RemoteAuthService,
        channel: Channel,
        encoder: JSONEncoder
    ) {
        let resp = StatusResponse(
            server: .init(
                version: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.0.0",
                apiVersion: "1.0.0",
                uptimeSeconds: serverRef?.uptimeSeconds ?? 0,
                port: preferences.remoteControlPort,
                tls: "self-signed",
                bonjourPublished: preferences.bonjourEnabled
            ),
            connections: .init(
                connectedDevices: authService.connectedDevices.count,
                maxDevices: 3,
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
        if let data = try? encoder.encode(resp) {
            channel.eventLoop.execute { [weak self] in
                self?.sendRawJSON(status: .ok, data: data, channel: channel)
            }
        }
    }

    // MARK: - Endpoint Handlers: Project Activate / Assistant Start / Stop

    @MainActor
    private func handleActivateProject(
        projectId: UUID,
        projectManager: any ProjectManaging,
        channel: Channel,
        encoder: JSONEncoder
    ) {
        guard projectManager.project(for: projectId) != nil else {
            let resp = ErrorResponse(
                error: ErrorDetail(code: "PROJECT_NOT_FOUND", message: "Unknown project ID.")
            )
            if let data = try? encoder.encode(resp) {
                channel.eventLoop.execute { [weak self] in
                    self?.sendRawJSON(status: .notFound, data: data, channel: channel)
                }
            }
            return
        }
        projectManager.activeProjectId = projectId
        let resp = OKResponse(ok: true)
        if let data = try? encoder.encode(resp) {
            channel.eventLoop.execute { [weak self] in
                self?.sendRawJSON(status: .ok, data: data, channel: channel)
            }
        }
    }

    @MainActor
    private func handleAssistantStart(
        body: ByteBuffer?,
        projectManager: any ProjectManaging,
        terminalService: TerminalService,
        channel: Channel,
        encoder: JSONEncoder
    ) {
        // Resolve active project.
        guard let projectId = projectManager.activeProjectId else {
            let resp = ErrorResponse(
                error: ErrorDetail(code: "NO_ACTIVE_PROJECT", message: "No active project is set.")
            )
            if let data = try? encoder.encode(resp) {
                channel.eventLoop.execute { [weak self] in
                    self?.sendRawJSON(status: .badRequest, data: data, channel: channel)
                }
            }
            return
        }

        // Parse optional request body for the `assistant` field.
        let requestedAssistant: AIAssistant? = {
            guard let buf = body,
                  let bytes = buf.getBytes(at: buf.readerIndex, length: buf.readableBytes),
                  let parsed = try? decoder.decode(AssistantStartRequest.self, from: Data(bytes)) else {
                return nil
            }
            return AIAssistant(rawValue: parsed.assistant ?? "")
        }()

        // Default to .claude when absent or unrecognised.
        let agent = requestedAssistant ?? .claude

        // Find the first non-agent shell session for the project.
        guard let shellSession = terminalService.sessions(for: projectId)
                .first(where: { !$0.isAgentSession }) else {
            let resp = ErrorResponse(
                error: ErrorDetail(
                    code: "NO_SHELL_SESSION",
                    message: "No shell session available for the active project."
                )
            )
            if let data = try? encoder.encode(resp) {
                channel.eventLoop.execute { [weak self] in
                    self?.sendRawJSON(status: .serviceUnavailable, data: data, channel: channel)
                }
            }
            return
        }

        // Send the launch command into the shell session.
        terminalService.sendInput(agent.launchCommand, to: shellSession.id)

        let resp = AssistantStartResponse(
            ok: true,
            assistant: agent.rawValue,
            sessionId: shellSession.id.uuidString
        )
        if let data = try? encoder.encode(resp) {
            channel.eventLoop.execute { [weak self] in
                self?.sendRawJSON(status: .ok, data: data, channel: channel)
            }
        }
    }

    @MainActor
    private func handleAssistantStop(
        projectManager: any ProjectManaging,
        terminalService: TerminalService,
        channel: Channel,
        encoder: JSONEncoder
    ) {
        guard let projectId = projectManager.activeProjectId else {
            let resp = ErrorResponse(
                error: ErrorDetail(code: "NO_ACTIVE_PROJECT", message: "No active project is set.")
            )
            if let data = try? encoder.encode(resp) {
                channel.eventLoop.execute { [weak self] in
                    self?.sendRawJSON(status: .badRequest, data: data, channel: channel)
                }
            }
            return
        }

        let sessions = terminalService.sessions(for: projectId)

        // Prefer the agent session; fall back to the first available session.
        guard let targetSession = sessions.first(where: { $0.isAgentSession }) ?? sessions.first else {
            let resp = ErrorResponse(
                error: ErrorDetail(
                    code: "NO_SESSION",
                    message: "No terminal session found for the active project."
                )
            )
            if let data = try? encoder.encode(resp) {
                channel.eventLoop.execute { [weak self] in
                    self?.sendRawJSON(status: .serviceUnavailable, data: data, channel: channel)
                }
            }
            return
        }

        // Determine exit sequence from the agent session title, defaulting to ctrlC.
        // We cannot know which assistant is running server-side without additional
        // state tracking, so we default to the universal Ctrl+C interrupt. Callers
        // that need agent-specific exit (e.g. claude's /exit) should track assistant
        // state themselves.
        terminalService.sendInput("\u{03}", to: targetSession.id)

        let resp = OKResponse(ok: true)
        if let data = try? encoder.encode(resp) {
            channel.eventLoop.execute { [weak self] in
                self?.sendRawJSON(status: .ok, data: data, channel: channel)
            }
        }
    }

    // MARK: - WebSocket Upgrade

    // MARK: - Helpers: Project Response Builder

    @MainActor
    private func buildProjectResponse(
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
        return ProjectResponse(
            id: project.id.uuidString,
            name: project.name,
            path: project.path.path,
            color: project.color?.value,
            isActive: isActive,
            git: nil,
            sessions: sessions
        )
    }

    // MARK: - WebSocket Upgrade

    /// Manually perform WebSocket upgrade (RFC 6455).
    ///
    /// NIO's `HTTPServerUpgradeHandler` is one-shot — it removes itself from the
    /// pipeline after the first non-upgrade request. On HTTP/1.1 keep-alive
    /// connections (Safari's default), the HTML/CSS/JS/REST requests arrive first,
    /// the upgrader is gone, and the WS upgrade request falls through to the router
    /// as a plain GET. This method handles the upgrade directly.
    private func handleWebSocketUpgrade(head: HTTPRequestHead, path: String, channel: Channel) {
        // Extract session ID.
        let sessionIdStr = String(path.dropFirst("/api/v1/terminal/".count))
        guard let sessionId = UUID(uuidString: sessionIdStr) else {
            sendErrorJSON(status: .badRequest, code: "INVALID_SESSION",
                          message: "Invalid session ID.", channel: channel)
            return
        }

        // Token from query params (browser WS API doesn't support Authorization header).
        let queryItems = URLComponents(string: head.uri)?.queryItems
        guard let token = queryItems?.first(where: { $0.name == "token" })?.value else {
            sendErrorJSON(status: .unauthorized, code: "AUTH_REQUIRED",
                          message: "Token required in query params.", channel: channel)
            return
        }

        guard let wsKey = head.headers["Sec-WebSocket-Key"].first else {
            sendErrorJSON(status: .badRequest, code: "INVALID_REQUEST",
                          message: "Missing Sec-WebSocket-Key.", channel: channel)
            return
        }

        let clientIP = remoteAddress
        let authSvc = authService
        let termSvc = terminalService
        let prefs = preferences
        let requestedProtocol = head.headers["Sec-WebSocket-Protocol"].first
        weak var server = serverRef

        #if DEBUG
        HTTPRequestRouter.wsLog("[WS] handleWebSocketUpgrade: session=\(sessionId) token=\(String(token.prefix(8)))... ip=\(clientIP)")
        #endif

        Task { @MainActor in
            let result = authSvc.validateToken(token, clientIP: clientIP)
            switch result {
            case .failure(let err):
                #if DEBUG
                HTTPRequestRouter.wsLog("[WS] auth FAILED: \(err) ip=\(clientIP)")
                #endif
                let (status, code, message) = self.authErrorResponse(err)
                channel.eventLoop.execute {
                    self.sendErrorJSON(status: status, code: code, message: message, channel: channel)
                }

            case .success(let device):
                // Sec-WebSocket-Accept per RFC 6455 §4.2.2.
                let magicGUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
                let hash = Insecure.SHA1.hash(data: Data((wsKey + magicGUID).utf8))
                let acceptValue = Data(hash).base64EncodedString()
                #if DEBUG
                HTTPRequestRouter.wsLog("[WS] auth OK: device=\(device.id) → sending 101")
                #endif

                let wsHandler = RemoteWebSocketHandler(
                    sessionId: sessionId,
                    deviceInfo: device,
                    serverRef: server,
                    terminalService: termSvc,
                    idleTimeoutMinutes: prefs.idleTimeoutMinutes
                )

                channel.eventLoop.execute {
                    // Pause reads so incoming WS frames don't hit the HTTP decoder
                    // during the pipeline swap (race condition: Safari sends WS frame
                    // immediately after receiving 101).
                    channel.setOption(ChannelOptions.autoRead, value: false)
                        .whenFailure { _ in }

                    // Send 101 Switching Protocols via the existing HTTP encoder.
                    var headers = HTTPHeaders()
                    headers.add(name: "Upgrade", value: "websocket")
                    headers.add(name: "Connection", value: "upgrade")
                    headers.add(name: "Sec-WebSocket-Accept", value: acceptValue)
                    if let proto = requestedProtocol {
                        headers.add(name: "Sec-WebSocket-Protocol", value: proto)
                    }

                    let responseHead = HTTPResponseHead(
                        version: .http1_1, status: .switchingProtocols, headers: headers
                    )
                    channel.write(self.wrapOutboundOut(.head(responseHead)), promise: nil)
                    channel.writeAndFlush(self.wrapOutboundOut(.end(nil))).whenSuccess {
                        self.installWebSocketPipeline(channel: channel, handler: wsHandler)
                    }
                }
            }
        }
    }

    /// Replace the HTTP pipeline with WebSocket frame codec + handler.
    private func installWebSocketPipeline(channel: Channel, handler: RemoteWebSocketHandler) {
        #if DEBUG
        HTTPRequestRouter.wsLog("[WS] installWebSocketPipeline: starting pipeline swap")
        #endif

        let el = channel.eventLoop
        let pipeline = channel.pipeline

        // Helper: remove a handler by name, ignoring "not found" errors.
        func removeByName(_ name: String) -> EventLoopFuture<Void> {
            pipeline.removeHandler(name: name).flatMapError { _ in
                el.makeSucceededVoidFuture()
            }
        }

        // Remove all HTTP handlers by name (set in RemoteControlServer configureChildChannel),
        // then install WebSocket handlers.
        removeByName("http_router").flatMap { () -> EventLoopFuture<Void> in
            #if DEBUG
            HTTPRequestRouter.wsLog("[WS] step1: router removed")
            #endif
            return removeByName("http_error")
        }.flatMap { () -> EventLoopFuture<Void> in
            #if DEBUG
            HTTPRequestRouter.wsLog("[WS] step2: error handler removed")
            #endif
            return removeByName("http_decoder")
        }.flatMap { () -> EventLoopFuture<Void> in
            #if DEBUG
            HTTPRequestRouter.wsLog("[WS] step3: decoder removed")
            #endif
            return removeByName("http_encoder")
        }.flatMap { () -> EventLoopFuture<Void> in
            #if DEBUG
            HTTPRequestRouter.wsLog("[WS] step4: encoder removed — adding WS handlers")
            #endif
            return pipeline.addHandler(WebSocketFrameEncoder())
        }.flatMap { () -> EventLoopFuture<Void> in
            pipeline.addHandler(ByteToMessageHandler(WebSocketFrameDecoder()))
        }.flatMap { () -> EventLoopFuture<Void> in
            pipeline.addHandler(handler)
        }.whenComplete { result in
            switch result {
            case .success:
                #if DEBUG
                HTTPRequestRouter.wsLog("[WS] pipeline swap COMPLETE — handler installed")
                #endif
                // Resume reading: WS decoder now in place.
                channel.setOption(ChannelOptions.autoRead, value: true).whenSuccess {
                    channel.read()
                }
            case .failure(let error):
                #if DEBUG
                HTTPRequestRouter.wsLog("[WS] pipeline swap FAILED: \(error)")
                #endif
                channel.close(promise: nil)
            }
        }
    }

    // MARK: - Helpers: Response Writing

    private func sendRawJSON(status: HTTPResponseStatus, data: Data, channel: Channel) {
        var headers = HTTPHeaders()
        headers.add(name: "Content-Type", value: "application/json; charset=utf-8")
        headers.add(name: "Content-Length", value: "\(data.count)")
        headers.add(name: "API-Version", value: "1.0.0")
        headers.add(name: "Cache-Control", value: "no-store")
        headers.add(name: "X-Content-Type-Options", value: "nosniff")
        headers.add(name: "X-Frame-Options", value: "DENY")
        if let origin = corsOrigin {
            headers.add(name: "Access-Control-Allow-Origin", value: origin)
            headers.add(name: "Vary", value: "Origin")
            headers.add(name: "Access-Control-Allow-Headers", value: "Authorization, Content-Type")
        }

        let head = HTTPResponseHead(version: .http1_1, status: status, headers: headers)
        var buffer = channel.allocator.buffer(capacity: data.count)
        buffer.writeBytes(data)

        channel.write(wrapOutboundOut(.head(head)), promise: nil)
        channel.write(wrapOutboundOut(.body(.byteBuffer(buffer))), promise: nil)
        channel.writeAndFlush(wrapOutboundOut(.end(nil)), promise: nil)
    }

    private func sendEncodable<T: Encodable>(_ value: T, channel: Channel, encoder: JSONEncoder) {
        guard let data = try? encoder.encode(value) else { return }
        channel.eventLoop.execute { [weak self] in
            self?.sendRawJSON(status: .ok, data: data, channel: channel)
        }
    }

    private func sendErrorJSON(
        status: HTTPResponseStatus,
        code: String,
        message: String,
        channel: Channel
    ) {
        let resp = ErrorResponse(error: ErrorDetail(code: code, message: message))
        guard let data = try? encoder.encode(resp) else { return }
        sendRawJSON(status: status, data: data, channel: channel)
    }

    private func sendEmptyResponse(status: HTTPResponseStatus, channel: Channel) {
        var headers = HTTPHeaders()
        headers.add(name: "Content-Length", value: "0")

        if let origin = corsOrigin {
            headers.add(name: "Access-Control-Allow-Origin", value: origin)
            headers.add(name: "Vary", value: "Origin")
        }

        let head = HTTPResponseHead(version: .http1_1, status: status, headers: headers)
        channel.write(wrapOutboundOut(.head(head)), promise: nil)
        channel.writeAndFlush(wrapOutboundOut(.end(nil)), promise: nil)
    }

    private func sendCORSPreflight(channel: Channel) {
        var headers = HTTPHeaders()
        if let origin = corsOrigin {
            headers.add(name: "Access-Control-Allow-Origin", value: origin)
            headers.add(name: "Vary", value: "Origin")
            headers.add(name: "Access-Control-Allow-Methods", value: "GET, POST, DELETE, OPTIONS")
            headers.add(name: "Access-Control-Allow-Headers", value: "Authorization, Content-Type, X-Request-Id")
            headers.add(name: "Access-Control-Max-Age", value: "86400")
        }
        headers.add(name: "Content-Length", value: "0")


        let head = HTTPResponseHead(version: .http1_1, status: .noContent, headers: headers)
        channel.write(wrapOutboundOut(.head(head)), promise: nil)
        channel.writeAndFlush(wrapOutboundOut(.end(nil)), promise: nil)
    }

    // MARK: - Helpers: Static Files

    private func serveStaticFile(_ fileName: String, contentType: String, channel: Channel) {
        guard let baseURL = Bundle.main.url(forResource: "RemoteControlWeb", withExtension: nil) else {
            sendErrorJSON(status: .notFound, code: "NOT_FOUND", message: "Static file not found.", channel: channel)
            return
        }
        let resolved = baseURL.appendingPathComponent(fileName).standardized
        guard resolved.path.hasPrefix(baseURL.standardized.path) else {
            sendErrorJSON(status: .forbidden, code: "FORBIDDEN", message: "Access denied.", channel: channel)
            return
        }
        guard let data = try? Data(contentsOf: resolved) else {
            sendErrorJSON(
                status: .notFound,
                code: "NOT_FOUND",
                message: "Static file not found.",
                channel: channel
            )
            return
        }

        var headers = HTTPHeaders()
        headers.add(name: "Content-Type", value: contentType)
        headers.add(name: "Content-Length", value: "\(data.count)")
        // Vendor files are immutable; app files get short cache for dev convenience.
        let isVendor = fileName.hasPrefix("vendor/")
        headers.add(name: "Cache-Control", value: isVendor
            ? "public, max-age=31536000, immutable"
            : "no-cache"
        )
        headers.add(name: "X-Content-Type-Options", value: "nosniff")
        headers.add(name: "X-Frame-Options", value: "DENY")


        let head = HTTPResponseHead(version: .http1_1, status: .ok, headers: headers)
        var buffer = channel.allocator.buffer(capacity: data.count)
        buffer.writeBytes(data)

        channel.write(wrapOutboundOut(.head(head)), promise: nil)
        channel.write(wrapOutboundOut(.body(.byteBuffer(buffer))), promise: nil)
        channel.writeAndFlush(wrapOutboundOut(.end(nil)), promise: nil)
    }

    // MARK: - Helpers: Parsing

    /// Build a safe CORS origin value. Only reflects the Origin header when it
    /// matches `localhost`, `127.0.0.1`, or `[::1]` on any port (the server's
    /// own Web UI). All other origins are rejected with `nil`, which means no
    /// `Access-Control-Allow-Origin` header is emitted.
    private func allowedOrigin(from head: HTTPRequestHead) -> String? {
        guard let origin = head.headers["Origin"].first,
              let url = URL(string: origin),
              let host = url.host else {
            // No Origin header — same-origin or non-browser request; no CORS header needed.
            return nil
        }
        let allowedHosts = ["localhost", "127.0.0.1", "[::1]"]
        return allowedHosts.contains(host) ? origin : nil
    }

    private func extractBearerToken(from head: HTTPRequestHead) -> String? {
        guard let auth = head.headers["Authorization"].first,
              auth.hasPrefix("Bearer ") else { return nil }
        return String(auth.dropFirst(7))
    }

    private func extractClientIP(from head: HTTPRequestHead) -> String {
        // Return the real TCP-level address captured at connection time.
        // X-Forwarded-For is intentionally ignored -- it is trivially spoofable
        // and must never be trusted for rate limiting or token binding.
        remoteAddress
    }

    private func extractUUID(from path: String, pattern: String) -> UUID? {
        guard path.hasPrefix(pattern) else { return nil }
        let remaining = String(path.dropFirst(pattern.count))
        // Take until next "/" or end of string.
        let idString = remaining.split(separator: "/").first.map(String.init) ?? remaining
        return UUID(uuidString: idString)
    }

    /// Extract both project ID and session ID from a path like
    /// `/api/v1/projects/{pid}/sessions/{sid}/scrollback`.
    private func extractProjectSession(from path: String) -> (UUID, UUID)? {
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

    private func mimeType(for ext: String) -> String {
        switch ext.lowercased() {
        case "js": return "application/javascript"
        case "css": return "text/css"
        case "html": return "text/html"
        case "json": return "application/json"
        case "png": return "image/png"
        case "svg": return "image/svg+xml"
        case "ico": return "image/x-icon"
        case "woff2": return "font/woff2"
        case "woff": return "font/woff"
        default: return "application/octet-stream"
        }
    }

    private func authErrorResponse(_ error: AuthError) -> (HTTPResponseStatus, String, String) {
        switch error {
        case .invalidPin:
            return (.unauthorized, "AUTH_PIN_INVALID", "Incorrect PIN.")
        case .rateLimited(retryAfterSeconds: let seconds):
            return (.tooManyRequests, "AUTH_LOCKOUT", "Too many failed attempts. Try again in \(seconds) seconds.")
        case .globalLockout:
            return (.tooManyRequests, "AUTH_LOCKOUT", "Server is locked due to excessive failed attempts.")
        case .invalidToken:
            return (.unauthorized, "AUTH_TOKEN_INVALID", "Invalid or expired token.")
        case .tokenExpired:
            return (.unauthorized, "AUTH_TOKEN_EXPIRED", "Token has expired. Please re-authenticate.")
        case .ipMismatch:
            return (.forbidden, "AUTH_IP_MISMATCH", "Token was issued to a different IP address.")
        case .maxDevicesReached:
            return (.forbidden, "AUTH_MAX_DEVICES", "Maximum number of connected devices reached.")
        }
    }
}

// MARK: - Result + isSuccess

extension Result {
    /// Convenience to check if the result is a success case.
    var isSuccess: Bool {
        if case .success = self { return true }
        return false
    }
}

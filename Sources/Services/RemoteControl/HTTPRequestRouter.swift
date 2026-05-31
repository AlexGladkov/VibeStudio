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

    /// General app preferences (Claude permission flag, etc.) -- same pattern.
    /// M13: injected so the router reads `claudeSkipPermissions` from the
    /// single source of truth instead of poking `UserDefaults.standard`
    /// directly, which would silently diverge from `GeneralPreferences` and
    /// also bypass `@Observable` notifications.
    private let generalPreferences: GeneralPreferences

    /// Cached idle timeout (captured at init on MainActor) for use in
    /// nonisolated NIO handler context (WebSocket upgrade).
    private let cachedIdleTimeoutMinutes: Int

    /// Weak reference to the server for bridge registration.
    private weak var serverRef: RemoteControlServer?

    /// In-memory static file cache pre-loaded at server start.
    /// Keys are relative paths like `"index.html"` or `"vendor/xterm.js"`.
    /// Values are `(data, contentType)`. Empty when the bundle is missing
    /// (falls back to disk reads in `serveStaticFile`).
    private let staticFileCache: [String: (Data, String)]

    /// Shared reference to the current ngrok tunnel hostname.
    /// Updated by ``RemoteControlServer`` when the ngrok URL is resolved.
    /// Read here to enforce exact-host CORS instead of a broad wildcard.
    private let ngrokHostRef: NgrokHostRef

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
        let ts = HTTPRequestRouter.isoFormatter.string(from: Date())
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

    /// Shared ISO-8601 date formatter.
    ///
    /// L11: `ISO8601DateFormatter` is expensive to construct (parses locale,
    /// calendar identifiers). Reusing a single instance avoids allocating one
    /// per request. The class is documented thread-safe by Apple.
    private static let isoFormatter = ISO8601DateFormatter()

    // MARK: - Init

    init(
        authService: RemoteAuthService,
        terminalService: TerminalService,
        projectManager: any ProjectManaging,
        preferences: RemoteControlPreferences,
        generalPreferences: GeneralPreferences,
        idleTimeoutMinutes: Int,
        serverRef: RemoteControlServer?,
        staticFileCache: [String: (Data, String)] = [:],
        ngrokHostRef: NgrokHostRef = NgrokHostRef()
    ) {
        self.authService = authService
        self.terminalService = terminalService
        self.projectManager = projectManager
        self.preferences = preferences
        self.generalPreferences = generalPreferences
        self.cachedIdleTimeoutMinutes = idleTimeoutMinutes
        self.serverRef = serverRef
        self.staticFileCache = staticFileCache
        self.ngrokHostRef = ngrokHostRef
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
                // Send 413 Payload Too Large and close the channel so the
                // connection does not hang waiting for a response.
                requestHead = nil
                requestBody = nil
                let channel = context.channel
                let json = #"{"error":{"code":"PAYLOAD_TOO_LARGE","message":"Request body exceeds the 64 KB limit."}}"#
                let data = Data(json.utf8)
                var headers = HTTPHeaders()
                headers.add(name: "Content-Type", value: "application/json; charset=utf-8")
                headers.add(name: "Content-Length", value: "\(data.count)")
                headers.add(name: "Connection", value: "close")
                let head = HTTPResponseHead(
                    version: .http1_1,
                    status: .payloadTooLarge,
                    headers: headers
                )
                var buffer = channel.allocator.buffer(capacity: data.count)
                buffer.writeBytes(data)
                channel.write(wrapOutboundOut(.head(head)), promise: nil)
                channel.write(wrapOutboundOut(.body(.byteBuffer(buffer))), promise: nil)
                channel.writeAndFlush(wrapOutboundOut(.end(nil))).whenComplete { _ in
                    channel.close(promise: nil)
                }
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
        // Debug endpoints are gated behind an opt-in environment variable
        // (VS_DEBUG_API=1) so they are never accidentally reachable in
        // TestFlight / Ad-Hoc builds distributed to external testers.
        // Set `VS_DEBUG_API=1` in the scheme's Run > Arguments > Environment
        // Variables to enable these endpoints locally.
        let debugAPIEnabled = ProcessInfo.processInfo.environment["VS_DEBUG_API"] != nil

        // Debug-only: expose current PIN for automated testing.
        if method == .GET && path == "/api/v1/debug/pin" {
            guard debugAPIEnabled else {
                sendErrorJSON(status: .notFound, code: "NOT_FOUND",
                              message: "The requested resource was not found.", channel: channel)
                return
            }
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
            guard debugAPIEnabled else {
                sendErrorJSON(status: .notFound, code: "NOT_FOUND",
                              message: "The requested resource was not found.", channel: channel)
                return
            }
            let log = HTTPRequestRouter.wsDebugLog
            let json = "[" + log.map { "\"\($0.replacingOccurrences(of: "\"", with: "\\\""))\"" }.joined(separator: ",") + "]"
            sendRawJSON(status: .ok, data: Data(json.utf8), channel: channel)
            return
        }
        // Debug-only: show connected devices, tokens, IPs, and caller IP.
        if method == .GET && path == "/api/v1/debug/state" {
            guard debugAPIEnabled else {
                sendErrorJSON(status: .notFound, code: "NOT_FOUND",
                              message: "The requested resource was not found.", channel: channel)
                return
            }
            let authSvc = authService
            let callerIP = remoteAddress
            let serverRef = self.serverRef
            Task { @MainActor in
                let devices = authSvc.connectedDevices.map { d in
                    "{\"id\":\"\(d.id)\",\"ip\":\"\(d.ipAddress)\"}"
                }.joined(separator: ",")
                let deviceCount = authSvc.connectedDevices.count
                let ngrokURL = serverRef?.ngrokTunnelURL ?? "null"
                let ngrokRunning = serverRef?.isNgrokRunning ?? false
                let ngrokError = serverRef?.ngrokError ?? "null"
                let json = """
                {"caller_ip":"\(callerIP)","devices":[\(devices)],"device_count":\(deviceCount),\
                "ngrok":{"running":\(ngrokRunning),"url":"\(ngrokURL)","error":"\(ngrokError)"}}
                """
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
        // Token is NOT in URL (would leak to logs/history). Auth occurs on first
        // WebSocket text frame: {"type":"auth","token":...}.
        if method == .GET && path.hasPrefix("/api/v1/terminal/") &&
           head.headers["Upgrade"].first?.caseInsensitiveCompare("websocket") == .orderedSame {
            handleWebSocketUpgrade(head: head, path: path, channel: channel)
            return
        }

        // All other /api/ endpoints require authentication.
        if path.hasPrefix("/api/") {
            let clientIP = currentClientIP
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
                    let (status, code, message, retryAfter) = self.authErrorResponse(error)
                    let respData = try? enc.encode(
                        ErrorResponse(error: ErrorDetail(code: code, message: message))
                    )
                    channel.eventLoop.execute { [weak self] in
                        self?.sendRawJSON(
                            status: status,
                            data: respData ?? Data(),
                            channel: channel,
                            retryAfterSeconds: retryAfter
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
                expiresAt: device.connectedAt.addingTimeInterval(RemoteAuthService.tokenTTL)
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

        // GET /api/v1/projects/recent (must be before :id pattern)
        if method == .GET && path == "/api/v1/projects/recent" {
            handleListRecentProjects(
                projectManager: projectManager,
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
                requestingDevice: device,
                serverRef: serverRef,
                channel: channel,
                encoder: encoder
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

        // POST /api/v1/projects/open
        if method == .POST && path == "/api/v1/projects/open" {
            handleOpenProject(
                body: body,
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
        // SECURITY (M1): `/api/v1/health` is unauthenticated and reachable
        // through the ngrok tunnel, so the response must not leak runtime
        // diagnostics that aid fingerprinting (version, uptime, connected
        // device count, TLS mode). Only emit the minimum status payload that
        // monitoring tools need. Authenticated callers can still query
        // `/api/v1/status` for the full diagnostics surface.
        let json = #"{"status":"healthy"}"#
        sendRawJSON(status: .ok, data: Data(json.utf8), channel: channel)
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

        let clientIP = currentClientIP
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
                // SECURITY (I1): never log token material. Even the first 8 hex
                // chars of a 32-byte token reduce its effective entropy when
                // logs are exposed (oslog snapshots, Console.app captures).
                NSLog("[RC-AUTH] auth success ip=\(clientIP)")
                #endif
                let resp = AuthTokenResponseDTO(
                    token: tokenResponse.token,
                    expiresAt: Date().addingTimeInterval(RemoteAuthService.tokenTTL),
                    deviceId: tokenResponse.device.id.uuidString
                )
                if let data = try? enc.encode(resp) {
                    channel.eventLoop.execute { [weak self] in
                        self?.sendRawJSON(status: .ok, data: data, channel: channel)
                    }
                }

            case .failure(let error):
                let (status, code, message, retryAfter) = self.authErrorResponse(error)
                let resp = ErrorResponse(error: ErrorDetail(code: code, message: message))
                if let data = try? enc.encode(resp) {
                    channel.eventLoop.execute { [weak self] in
                        self?.sendRawJSON(
                            status: status,
                            data: data,
                            channel: channel,
                            retryAfterSeconds: retryAfter
                        )
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
        sendEncodableResponse(resp, status: .ok, channel: channel, encoder: encoder)
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
            sendEncodableResponse(resp, status: .notFound, channel: channel, encoder: encoder)
            return
        }
        let resp = buildProjectResponse(
            project: project,
            isActive: project.id == projectManager.activeProjectId,
            terminalService: terminalService,
            serverRef: serverRef
        )
        sendEncodableResponse(resp, status: .ok, channel: channel, encoder: encoder)
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
        // Allow scrollback access for any authenticated device (valid bearer token).
        // Previously this required an active WS bridge (`isAttached`), which broke
        // reconnect flows: the client needs scrollback to restore its view before
        // the WS handshake completes. Authentication is sufficient authorization
        // here — the session belongs to the local user who chose to share it.
        // (No session ownership check is needed because all remote clients share
        // the same local user context.)

        let fullContent = terminalService.rawScrollbackContent(for: sessionId) ?? ""
        let allLines = fullContent.components(separatedBy: "\n")
        // Cap returned content to last 10000 lines to prevent excessive payloads.
        let maxLines = 10_000
        let returnedLines = allLines.suffix(maxLines)
        let content = returnedLines.joined(separator: "\n")
        let resp = ScrollbackResponse(
            content: content,
            totalLines: allLines.count,
            returnedLines: returnedLines.count
        )
        sendEncodableResponse(resp, status: .ok, channel: channel, encoder: encoder)
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
        let resp = DevicesListResponse(devices: deviceResponses, maxDevices: RemoteAuthService.maxDevices)
        sendEncodableResponse(resp, status: .ok, channel: channel, encoder: encoder)
    }

    @MainActor
    private func handleDisconnectDevice(
        deviceId: UUID,
        requestingDevice: RemoteDevice,
        serverRef: RemoteControlServer?,
        channel: Channel,
        encoder: JSONEncoder
    ) {
        // SECURITY: A device can only disconnect itself.
        guard deviceId == requestingDevice.id else {
            let resp = ErrorResponse(
                error: ErrorDetail(
                    code: "FORBIDDEN",
                    message: "You can only disconnect your own device."
                )
            )
            sendEncodableResponse(resp, status: .forbidden, channel: channel, encoder: encoder)
            return
        }
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
        sendEncodableResponse(resp, status: .ok, channel: channel, encoder: encoder)
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
            sendEncodableResponse(resp, status: .notFound, channel: channel, encoder: encoder)
            return
        }
        projectManager.activeProjectId = projectId
        let resp = OKResponse(ok: true)
        sendEncodableResponse(resp, status: .ok, channel: channel, encoder: encoder)
    }

    @MainActor
    private func handleListRecentProjects(
        projectManager: any ProjectManaging,
        channel: Channel,
        encoder: JSONEncoder
    ) {
        let isoFormatter = HTTPRequestRouter.isoFormatter
        let recentResponses = projectManager.recentProjects.map { project in
            // SECURITY: Expose only the last path component (directory name), not
            // the full filesystem path, to prevent information leakage to remote clients.
            RecentProjectResponse(
                name: project.name,
                path: project.path.lastPathComponent,
                lastOpened: isoFormatter.string(from: project.lastOpened)
            )
        }
        let resp = RecentProjectsListResponse(projects: recentResponses)
        sendEncodableResponse(resp, status: .ok, channel: channel, encoder: encoder)
    }

    @MainActor
    private func handleOpenProject(
        body: ByteBuffer?,
        projectManager: any ProjectManaging,
        channel: Channel,
        encoder: JSONEncoder
    ) {
        guard let body,
              let bodyData = body.getBytes(at: body.readerIndex, length: body.readableBytes).map({ Data($0) }),
              let request = try? decoder.decode(OpenProjectRequest.self, from: bodyData) else {
            let resp = ErrorResponse(
                error: ErrorDetail(code: "INVALID_BODY", message: "Expected JSON with 'path' field.")
            )
            sendEncodableResponse(resp, status: .badRequest, channel: channel, encoder: encoder)
            return
        }

        let pathURL = URL(fileURLWithPath: request.path).standardizedFileURL

        // SECURITY: Reject paths outside user's home directory to prevent
        // arbitrary filesystem traversal. Only allow opening projects within
        // the user's home directory tree.
        let homePath = FileManager.default.homeDirectoryForCurrentUser.standardizedFileURL.path
        guard pathURL.path.hasPrefix(homePath) else {
            let resp = ErrorResponse(
                error: ErrorDetail(
                    code: "PATH_FORBIDDEN",
                    message: "Only paths within the user's home directory are allowed."
                )
            )
            sendEncodableResponse(resp, status: .forbidden, channel: channel, encoder: encoder)
            return
        }

        // If project already open — just activate it.
        if let existing = projectManager.project(at: pathURL) {
            projectManager.activeProjectId = existing.id
            let resp = OpenProjectResponse(ok: true, projectId: existing.id.uuidString)
            sendEncodableResponse(resp, status: .ok, channel: channel, encoder: encoder)
            return
        }

        do {
            let project = try projectManager.addProject(at: pathURL)
            projectManager.activeProjectId = project.id
            let resp = OpenProjectResponse(ok: true, projectId: project.id.uuidString)
            sendEncodableResponse(resp, status: .ok, channel: channel, encoder: encoder)
        } catch {
            // SECURITY (H2): do NOT echo `error.localizedDescription` to the
            // client — POSIX errors expose the full filesystem path including
            // the local user's home directory name. Log the detailed reason
            // server-side; return a fixed, non-revealing message to the caller.
            Logger.remoteControl.warning(
                "handleOpenProject: addProject failed for path: \(error.localizedDescription, privacy: .private)"
            )
            let resp = ErrorResponse(
                error: ErrorDetail(code: "OPEN_FAILED", message: "Failed to open project.")
            )
            sendEncodableResponse(resp, status: .badRequest, channel: channel, encoder: encoder)
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
            sendEncodableResponse(resp, status: .badRequest, channel: channel, encoder: encoder)
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
            sendEncodableResponse(resp, status: .serviceUnavailable, channel: channel, encoder: encoder)
            return
        }

        // M13: Build launch command via the centralised
        // `AIAssistant.launchCommand(skipPermissions:)` helper. The
        // skip-permissions flag is sourced from `GeneralPreferences` — the
        // single source of truth — instead of a duplicated UserDefaults read
        // that would silently drift if the preference key ever changed.
        let command = agent.launchCommand(
            skipPermissions: generalPreferences.claudeSkipPermissions
        )
        terminalService.sendInput(command, to: shellSession.id)

        let resp = AssistantStartResponse(
            ok: true,
            assistant: agent.rawValue,
            sessionId: shellSession.id.uuidString
        )
        sendEncodableResponse(resp, status: .ok, channel: channel, encoder: encoder)
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
            sendEncodableResponse(resp, status: .badRequest, channel: channel, encoder: encoder)
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
            sendEncodableResponse(resp, status: .serviceUnavailable, channel: channel, encoder: encoder)
            return
        }

        // Determine exit sequence from the agent session title, defaulting to ctrlC.
        // We cannot know which assistant is running server-side without additional
        // state tracking, so we default to the universal Ctrl+C interrupt. Callers
        // that need agent-specific exit (e.g. claude's /exit) should track assistant
        // state themselves.
        terminalService.sendInput("\u{03}", to: targetSession.id)

        let resp = OKResponse(ok: true)
        sendEncodableResponse(resp, status: .ok, channel: channel, encoder: encoder)
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
        // SECURITY: Only expose the last path component (directory name) instead
        // of the full filesystem path, to prevent information leakage.
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

        // SECURITY: Token is NOT passed in query params (would leak in logs/history).
        // Instead, the client sends the token as the first WS text message after connect.
        // Authentication is handled by RemoteWebSocketHandler.

        guard let wsKey = head.headers["Sec-WebSocket-Key"].first else {
            sendErrorJSON(status: .badRequest, code: "INVALID_REQUEST",
                          message: "Missing Sec-WebSocket-Key.", channel: channel)
            return
        }

        let clientIP = remoteAddress
        let authSvc = authService
        let termSvc = terminalService
        let idleTimeout = cachedIdleTimeoutMinutes
        let requestedProtocol = head.headers["Sec-WebSocket-Protocol"].first
        weak var server = serverRef

        #if DEBUG
        HTTPRequestRouter.wsLog("[WS] handleWebSocketUpgrade: session=\(sessionId) ip=\(clientIP)")
        #endif

        // Upgrade immediately — auth happens on first WS message.
        do {
            // Sec-WebSocket-Accept per RFC 6455 §4.2.2.
            let magicGUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
            let hash = Insecure.SHA1.hash(data: Data((wsKey + magicGUID).utf8))
            let acceptValue = Data(hash).base64EncodedString()

            // Create handler that expects auth as first message.
            let wsHandler = RemoteWebSocketHandler(
                sessionId: sessionId,
                deviceInfo: nil, // authenticated on first message
                serverRef: server,
                terminalService: termSvc,
                idleTimeoutMinutes: idleTimeout,
                authService: authSvc,
                clientIP: clientIP
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
                // SECURITY: Only echo the subprotocol if the client requested
                // exactly "vibestudio.v1". Echoing an arbitrary subprotocol back
                // to the client would allow an attacker to spoof our WebSocket
                // negotiation by claiming any protocol name.
                if requestedProtocol == "vibestudio.v1" {
                    headers.add(name: "Sec-WebSocket-Protocol", value: "vibestudio.v1")
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

    /// Append security headers common to all responses.
    ///
    /// - Parameters:
    ///   - headers: The mutable header set to augment.
    ///   - isHTML: When `true`, the `Content-Security-Policy` is tuned for
    ///     the web UI (allows inline styles, WebSocket connections).
    ///     When `false` (API JSON), a restrictive `default-src 'none'` policy
    ///     is emitted.
    private func addSecurityHeaders(to headers: inout HTTPHeaders, isHTML: Bool) {
        headers.add(name: "X-Content-Type-Options", value: "nosniff")
        headers.add(name: "X-Frame-Options", value: "DENY")
        headers.add(name: "Referrer-Policy", value: "no-referrer")
        if isHTML {
            // Web UI CSP: allow same-origin resources, inline styles for
            // xterm.js theming, and ws:/wss: for the terminal WebSocket.
            headers.add(
                name: "Content-Security-Policy",
                value: "default-src 'self'; " +
                       "script-src 'self'; " +
                       "style-src 'self' 'unsafe-inline'; " +
                       "connect-src 'self' wss: ws:"
            )
        } else {
            // API JSON responses have no need to load any sub-resources.
            headers.add(name: "Content-Security-Policy", value: "default-src 'none'")
        }
    }

    /// Send a JSON HTTP response.
    ///
    /// - Parameters:
    ///   - status: HTTP response status.
    ///   - data: Pre-encoded JSON body.
    ///   - channel: Target NIO channel.
    ///   - retryAfterSeconds: When non-nil, emits the `Retry-After` HTTP header
    ///     (RFC 7231 §7.1.3). Required for well-formed 429/503 responses so
    ///     clients honour back-off without parsing the application payload.
    private func sendRawJSON(
        status: HTTPResponseStatus,
        data: Data,
        channel: Channel,
        retryAfterSeconds: Int? = nil
    ) {
        var headers = HTTPHeaders()
        headers.add(name: "Content-Type", value: "application/json; charset=utf-8")
        headers.add(name: "Content-Length", value: "\(data.count)")
        headers.add(name: "API-Version", value: "1.0.0")
        headers.add(name: "Cache-Control", value: "no-store")
        if let retryAfterSeconds, retryAfterSeconds > 0 {
            // RFC 7231 §7.1.3 — delta-seconds form.
            headers.add(name: "Retry-After", value: "\(retryAfterSeconds)")
        }
        addSecurityHeaders(to: &headers, isHTML: false)
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
        sendEncodableResponse(value, status: .ok, channel: channel, encoder: encoder)
    }

    /// Encode a value and send as JSON response via eventLoop.execute.
    /// Consolidates the repeated encode → execute → sendRawJSON pattern.
    private func sendEncodableResponse<T: Encodable>(
        _ value: T,
        status: HTTPResponseStatus,
        channel: Channel,
        encoder: JSONEncoder
    ) {
        guard let data = try? encoder.encode(value) else { return }
        channel.eventLoop.execute { [weak self] in
            self?.sendRawJSON(status: status, data: data, channel: channel)
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
        // Fast path: serve from in-memory cache pre-loaded at server start.
        if let (cachedData, cachedContentType) = staticFileCache[fileName] {
            sendStaticFileData(
                data: cachedData,
                contentType: cachedContentType,
                fileName: fileName,
                channel: channel
            )
            return
        }

        // Fallback: read from disk (used when bundle was not found at startup,
        // e.g. during unit tests or if the cache is empty).
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
        sendStaticFileData(data: data, contentType: contentType, fileName: fileName, channel: channel)
    }

    private func sendStaticFileData(data: Data, contentType: String, fileName: String, channel: Channel) {
        var headers = HTTPHeaders()
        headers.add(name: "Content-Type", value: contentType)
        headers.add(name: "Content-Length", value: "\(data.count)")
        // Vendor files are immutable; app files get short cache for dev convenience.
        let isVendor = fileName.hasPrefix("vendor/")
        headers.add(name: "Cache-Control", value: isVendor
            ? "public, max-age=31536000, immutable"
            : "no-cache"
        )
        // HTML files get the web-UI CSP (allows inline styles + WebSocket).
        // Everything else (JS, CSS, vendor assets) gets the restrictive API CSP.
        let isHTML = contentType.hasPrefix("text/html")
        addSecurityHeaders(to: &headers, isHTML: isHTML)

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
    /// own Web UI), or the exact ngrok host currently stored in `ngrokHostRef`.
    ///
    /// The previous broad `*.ngrok-free.app` wildcard is replaced with an
    /// exact-host match: only the specific subdomain obtained from the ngrok
    /// local API is allowed. This prevents any other ngrok-free.app host from
    /// being accepted as a CORS origin.
    private func allowedOrigin(from head: HTTPRequestHead) -> String? {
        guard let origin = head.headers["Origin"].first,
              let url = URL(string: origin),
              let host = url.host else {
            // No Origin header — same-origin or non-browser request; no CORS header needed.
            return nil
        }
        let allowedHosts: Set<String> = ["localhost", "127.0.0.1", "[::1]"]
        if allowedHosts.contains(host) { return origin }

        // Allow only the exact ngrok host currently active (not a wildcard).
        if let ngrokHost = ngrokHostRef.host, host == ngrokHost {
            return origin
        }
        return nil
    }

    private func extractBearerToken(from head: HTTPRequestHead) -> String? {
        guard let auth = head.headers["Authorization"].first,
              auth.hasPrefix("Bearer ") else { return nil }
        return String(auth.dropFirst(7))
    }

    /// Real TCP-level client IP captured in `channelActive`.
    ///
    /// M11: previously exposed as `extractClientIP(from head:)` taking a
    /// `HTTPRequestHead` that the implementation ignored. Renamed to a
    /// computed property to communicate the actual behaviour and prevent
    /// confused callers from believing the header is consulted.
    ///
    /// X-Forwarded-For is intentionally NOT consulted — it is trivially
    /// spoofable and must never be trusted for rate limiting or token binding.
    private var currentClientIP: String { remoteAddress }

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

    /// Map an ``AuthError`` to (HTTP status, error code, human message,
    /// optional Retry-After seconds).
    ///
    /// SECURITY (L16): when the status is 429 (Too Many Requests), callers
    /// must emit a `Retry-After` HTTP header in addition to the JSON body so
    /// well-behaved HTTP clients (and crawlers) honour the back-off without
    /// having to parse the application-layer payload.
    private func authErrorResponse(_ error: AuthError) -> (HTTPResponseStatus, String, String, Int?) {
        switch error {
        case .invalidPin:
            return (.unauthorized, "AUTH_PIN_INVALID", "Incorrect PIN.", nil)
        case .rateLimited(retryAfterSeconds: let seconds):
            return (.tooManyRequests, "AUTH_LOCKOUT",
                    "Too many failed attempts. Try again in \(seconds) seconds.",
                    seconds)
        case .globalLockout:
            return (.tooManyRequests, "AUTH_LOCKOUT",
                    "Server is locked due to excessive failed attempts.",
                    // Global lockout has no automatic recovery — the operator
                    // must manually reset. Suggest a long retry window so
                    // honest clients don't hammer the endpoint.
                    Int(RemoteAuthService.rateLimitWindowSecondsPublic))
        case .invalidToken:
            return (.unauthorized, "AUTH_TOKEN_INVALID", "Invalid or expired token.", nil)
        case .tokenExpired:
            return (.unauthorized, "AUTH_TOKEN_EXPIRED", "Token has expired. Please re-authenticate.", nil)
        case .ipMismatch:
            return (.forbidden, "AUTH_IP_MISMATCH", "Token was issued to a different IP address.", nil)
        case .maxDevicesReached:
            // 503 Service Unavailable: the server is at capacity, not that the
            // client is forbidden. This lets the client distinguish "try later"
            // (503) from "you are not allowed" (403).
            return (.serviceUnavailable, "MAX_DEVICES_REACHED",
                    "Maximum number of connected devices reached. Try again later.",
                    nil)
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

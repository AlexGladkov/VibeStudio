// MARK: - HTTPRequestRouter
// NIO ChannelHandler that routes HTTP requests to API endpoint groups and static files.
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOHTTP1
import OSLog

/// NIO inbound channel handler that dispatches HTTP requests to the
/// Remote Control REST API, the WebSocket upgrade flow, and the embedded
/// static-file server.
///
/// **Threading model:**
/// This handler runs on a NIO `EventLoop` thread. Endpoint groups that need
/// `@MainActor` state (project lists, scrollback content, auth state) are
/// invoked via `Task { @MainActor in ... }`, then the resulting
/// `(HTTPResponseHead, ByteBuffer)` is written back through
/// `channel.eventLoop.execute`.
///
/// `ChannelHandlerContext` is NOT `Sendable` — it must not be captured in
/// `Task` closures. The cached `Channel` reference is used instead.
final class HTTPRequestRouter: ChannelInboundHandler, RemovableChannelHandler {

    typealias InboundIn = HTTPServerRequestPart
    typealias OutboundOut = HTTPServerResponsePart

    // MARK: - Dependencies (thread-safe references)

    private let authService: RemoteAuthService
    private let terminalService: TerminalService
    private let projectManager: any ProjectManaging
    private let preferences: RemoteControlPreferences
    /// Cached at init on MainActor — used by the nonisolated WS upgrade path.
    private let cachedIdleTimeoutMinutes: Int
    private weak var serverRef: RemoteControlServer?
    private let claudePermissions: any ClaudePermissionsResolving
    /// Upload store for remote chat attachments — passed to UploadEndpoints.
    private let uploadStore: RemoteUploadStore

    // MARK: - Collaborators

    private let builder: HTTPResponseBuilder
    private let corsPolicy: CORSPolicy
    private let ipExtractor: ClientIPExtractor
    private let staticFiles: StaticFileHandler
    private let wsUpgrade: WebSocketUpgradeHandler

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

    // MARK: - Request Accumulator

    private var requestHead: HTTPRequestHead?
    private var requestBody: ByteBuffer?

    // MARK: - JSON

    private let encoder: JSONEncoder
    private let decoder: JSONDecoder

    /// Build the JSON encoder used by all router-emitted responses.
    /// Kept as a static factory so it can be referenced during `init` without
    /// touching `self` before phase-1 completes.
    private static func makeEncoder() -> JSONEncoder {
        let e = JSONEncoder()
        e.dateEncodingStrategy = .iso8601
        return e
    }

    // MARK: - Init

    init(
        authService: RemoteAuthService,
        terminalService: TerminalService,
        projectManager: any ProjectManaging,
        preferences: RemoteControlPreferences,
        idleTimeoutMinutes: Int,
        serverRef: RemoteControlServer?,
        staticFileCache: [String: (Data, String)] = [:],
        ngrokHostRef: NgrokHostRef = NgrokHostRef(),
        claudePermissions: any ClaudePermissionsResolving,
        uploadStore: RemoteUploadStore
    ) {
        self.authService = authService
        self.terminalService = terminalService
        self.projectManager = projectManager
        self.preferences = preferences
        self.cachedIdleTimeoutMinutes = idleTimeoutMinutes
        self.serverRef = serverRef
        self.claudePermissions = claudePermissions
        self.uploadStore = uploadStore

        let encoder = Self.makeEncoder()
        self.encoder = encoder
        self.decoder = JSONDecoder()
        self.builder = HTTPResponseBuilder(encoder: encoder)
        self.corsPolicy = CORSPolicy(ngrokHostRef: ngrokHostRef)
        self.ipExtractor = ClientIPExtractor()
        self.staticFiles = StaticFileHandler(
            cache: staticFileCache,
            bundleRoot: Bundle.main.url(forResource: "RemoteControlWeb", withExtension: nil)
        )
        self.wsUpgrade = WebSocketUpgradeHandler()
    }

    // MARK: - ChannelInboundHandler

    func channelActive(context: ChannelHandlerContext) {
        remoteAddress = ipExtractor.clientIP(from: context.channel)
        context.fireChannelActive()
    }

    func channelRead(context: ChannelHandlerContext, data: NIOAny) {
        let part = unwrapInboundIn(data)

        switch part {
        case .head(let head):
            requestHead = head
            requestBody = context.channel.allocator.buffer(capacity: 0)

        case .body(var body):
            // Reject oversized request bodies. JSON endpoints are tiny, but
            // /api/v1/uploads/image needs up to UploadEndpoints.maxBytes.
            // Pick the larger of the two so uploads have headroom.
            let bodyLimit = max(65_536, UploadEndpoints.maxBytes)
            if (requestBody?.readableBytes ?? 0) + body.readableBytes > bodyLimit {
                requestHead = nil
                requestBody = nil
                let channel = context.channel
                let (head, buffer) = builder.payloadTooLargeResponse(allocator: channel.allocator)
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

        // SECURITY: `corsOrigin` is a request-scoped local — never an instance var.
        // Under HTTP keep-alive, a previous request's origin must not leak into a
        // subsequent response.
        let corsOrigin = corsPolicy.allowedOrigin(from: head)

        // CORS preflight.
        if method == .OPTIONS {
            let (h, b) = builder.corsPreflightResponse(corsOrigin: corsOrigin, allocator: channel.allocator)
            write(head: h, body: b, to: channel)
            return
        }

        // Static files (no auth required).
        if method == .GET, handleStatic(path: path, channel: channel) {
            return
        }

        // Health check (no auth required).
        if method == .GET && path == "/api/v1/health" {
            dispatchMainActor(channel: channel) { [authService, builder] alloc in
                DiagnosticsEndpoints(authService: authService, builder: builder)
                    .handleHealth(serverRef: self.serverRef, corsOrigin: corsOrigin, allocator: alloc)
            }
            return
        }

        // Auth (no bearer required).
        if method == .POST && path == "/api/v1/auth/token" {
            let clientIP = remoteAddress
            let userAgent = head.headers["User-Agent"].first ?? ""
            #if DEBUG
            NSLog("[RC-AUTH] handleAuthToken: clientIP=\(clientIP) remoteAddr=\(remoteAddress)")
            #endif
            dispatchMainActor(channel: channel) { [authService, builder, decoder] alloc in
                AuthEndpoints(authService: authService, builder: builder, decoder: decoder)
                    .handlePinValidate(
                        body: body, clientIP: clientIP, userAgent: userAgent,
                        corsOrigin: corsOrigin, allocator: alloc
                    )
            }
            return
        }

        #if DEBUG
        if method == .GET, path.hasPrefix("/api/v1/debug/") {
            handleDebugRoute(path: path, channel: channel, corsOrigin: corsOrigin)
            return
        }
        #endif

        // WebSocket upgrade for terminal endpoints.
        if method == .GET &&
           (path.hasPrefix("/ws/terminal/") || path.hasPrefix("/api/v1/terminal/")) &&
           head.headers["Upgrade"].first?.caseInsensitiveCompare("websocket") == .orderedSame {
            handleWebSocketUpgrade(head: head, path: path, channel: channel, corsOrigin: corsOrigin)
            return
        }

        // All other /api/ endpoints require authentication.
        if path.hasPrefix("/api/") {
            handleAuthenticated(
                path: path, method: method, head: head, body: body,
                channel: channel, corsOrigin: corsOrigin
            )
            return
        }

        // Unknown path.
        let (h, b) = builder.errorJSONResponse(
            status: .notFound, code: "NOT_FOUND",
            message: "The requested resource was not found.",
            corsOrigin: corsOrigin, allocator: channel.allocator
        )
        write(head: h, body: b, to: channel)
    }

    // MARK: - Static Files

    /// Returns `true` when the request matched a static route (response sent).
    private func handleStatic(path: String, channel: Channel) -> Bool {
        let mapping: [String: String] = [
            "/": "index.html",
            // `/index.html` must resolve too: the service worker pre-caches it
            // in SHELL_ASSETS via cache.addAll(), which is all-or-nothing — a
            // 404 here aborted the entire install and left the SW cache empty,
            // so returning users were served the SW's "Offline" 503 fallback.
            "/index.html": "index.html",
            "/app.css": "app.css",
            "/app.js": "app.js",
            "/sw.js": "sw.js",
            "/favicon.ico": "favicon.ico"
        ]
        if let fileName = mapping[path] {
            let defaultType = MIMETypeMap.forExtension((fileName as NSString).pathExtension)
            serveStaticFile(fileName: fileName, defaultContentType: defaultType, channel: channel)
            return true
        }
        if path.hasPrefix("/vendor/") {
            let fileName = String(path.dropFirst(1)) // "vendor/..."
            let ext = (fileName as NSString).pathExtension
            serveStaticFile(fileName: fileName, defaultContentType: MIMETypeMap.forExtension(ext), channel: channel)
            return true
        }
        return false
    }

    private func serveStaticFile(fileName: String, defaultContentType: String, channel: Channel) {
        switch staticFiles.serve(fileName: fileName, defaultContentType: defaultContentType) {
        case .found(let data, let contentType):
            let (h, b) = builder.staticFileResponse(
                data: data, contentType: contentType,
                fileName: fileName, allocator: channel.allocator
            )
            write(head: h, body: b, to: channel)
        case .forbidden:
            let (h, b) = builder.errorJSONResponse(
                status: .forbidden, code: "FORBIDDEN",
                message: "Access denied.", corsOrigin: nil, allocator: channel.allocator
            )
            write(head: h, body: b, to: channel)
        case .notFound:
            let (h, b) = builder.errorJSONResponse(
                status: .notFound, code: "NOT_FOUND",
                message: "Static file not found.", corsOrigin: nil, allocator: channel.allocator
            )
            write(head: h, body: b, to: channel)
        }
    }

    // MARK: - Authenticated Dispatch

    private func handleAuthenticated(
        path: String,
        method: HTTPMethod,
        head: HTTPRequestHead,
        body: ByteBuffer?,
        channel: Channel,
        corsOrigin: String?
    ) {
        let clientIP = remoteAddress
        guard let token = extractBearerToken(from: head) else {
            let (h, b) = builder.errorJSONResponse(
                status: .unauthorized, code: "AUTH_REQUIRED",
                message: "Authorization header with Bearer token is required.",
                corsOrigin: corsOrigin, allocator: channel.allocator
            )
            write(head: h, body: b, to: channel)
            return
        }

        let authSvc = authService
        let builder = builder
        let alloc = channel.allocator

        Task { @MainActor [weak self] in
            guard let self else { return }
            let result = authSvc.validateToken(token, clientIP: clientIP)
            switch result {
            case .failure(let error):
                let (status, code, message) = AuthEndpoints.authErrorResponse(error)
                let (h, b) = builder.errorJSONResponse(
                    status: status, code: code, message: message,
                    corsOrigin: corsOrigin, allocator: alloc
                )
                channel.eventLoop.execute { [weak self] in
                    self?.write(head: h, body: b, to: channel)
                }

            case .success(let device):
                let registry = RouterRegistry(
                    authService: self.authService,
                    terminalService: self.terminalService,
                    projectManager: self.projectManager,
                    preferences: self.preferences,
                    claudePermissions: self.claudePermissions,
                    uploadStore: self.uploadStore,
                    builder: builder,
                    decoder: self.decoder
                )
                // Forward request Content-Type to the registry so binary
                // uploads can be MIME-checked.
                let contentType = head.headers["Content-Type"].first ?? ""
                let response = registry.dispatch(
                    path: path, method: method, body: body, device: device,
                    token: token, clientIP: clientIP, contentType: contentType,
                    serverRef: self.serverRef, corsOrigin: corsOrigin, allocator: alloc
                )
                channel.eventLoop.execute { [weak self] in
                    if let (h, b) = response {
                        self?.write(head: h, body: b, to: channel)
                    }
                }
            }
        }
    }

    // MARK: - Debug Routes

    #if DEBUG
    private func handleDebugRoute(path: String, channel: Channel, corsOrigin: String?) {
        // Only the three known debug paths are dispatched here; anything else
        // under `/api/v1/debug/` falls through to the standard 404 below.
        let known: Set<String> = ["/api/v1/debug/pin", "/api/v1/debug/wslog", "/api/v1/debug/state"]
        guard known.contains(path) else {
            let (h, b) = builder.errorJSONResponse(
                status: .notFound, code: "NOT_FOUND",
                message: "The requested resource was not found.",
                corsOrigin: corsOrigin, allocator: channel.allocator
            )
            write(head: h, body: b, to: channel)
            return
        }
        let callerIP = remoteAddress
        dispatchMainActor(channel: channel) { [authService, builder, weak self] alloc in
            let debug = DebugEndpoints(authService: authService, builder: builder)
            guard DebugEndpoints.isEnabled else {
                return debug.handleDisabled(corsOrigin: corsOrigin, allocator: alloc)
            }
            switch path {
            case "/api/v1/debug/pin":
                return debug.handlePin(corsOrigin: corsOrigin, allocator: alloc)
            case "/api/v1/debug/wslog":
                return debug.handleWSLog(corsOrigin: corsOrigin, allocator: alloc)
            case "/api/v1/debug/state":
                return debug.handleState(
                    callerIP: callerIP, serverRef: self?.serverRef,
                    corsOrigin: corsOrigin, allocator: alloc
                )
            default:
                return nil
            }
        }
    }
    #endif

    // MARK: - WebSocket Upgrade

    private func handleWebSocketUpgrade(
        head: HTTPRequestHead,
        path: String,
        channel: Channel,
        corsOrigin: String?
    ) {
        switch wsUpgrade.parseSession(path: path, head: head) {
        case .invalidSession:
            let (h, b) = builder.errorJSONResponse(
                status: .badRequest, code: "INVALID_SESSION",
                message: "Invalid session ID.", corsOrigin: corsOrigin, allocator: channel.allocator
            )
            write(head: h, body: b, to: channel)

        case .missingKey:
            let (h, b) = builder.errorJSONResponse(
                status: .badRequest, code: "INVALID_REQUEST",
                message: "Missing Sec-WebSocket-Key.", corsOrigin: corsOrigin, allocator: channel.allocator
            )
            write(head: h, body: b, to: channel)

        case .ok(let sessionId):
            performWebSocketUpgrade(head: head, sessionId: sessionId, channel: channel)
        }
    }

    private func performWebSocketUpgrade(
        head: HTTPRequestHead,
        sessionId: UUID,
        channel: Channel
    ) {
        // wsKey existence is guaranteed by parseSession.
        guard let wsKey = head.headers["Sec-WebSocket-Key"].first else { return }
        let requestedProtocol = head.headers["Sec-WebSocket-Protocol"].first
        let upgrade = wsUpgrade.buildUpgradeResponse(
            secWebSocketKey: wsKey, requestedProtocol: requestedProtocol
        )

        let clientIP = remoteAddress
        let authSvc = authService
        let termSvc = terminalService
        let idleTimeout = cachedIdleTimeoutMinutes
        weak var server = serverRef

        #if DEBUG
        HTTPRequestRouter.wsLog("[WS] handleWebSocketUpgrade: session=\(sessionId) ip=\(clientIP)")
        #endif

        let wsHandler = RemoteWebSocketHandler(
            sessionId: sessionId,
            deviceInfo: nil, // authenticated on first message
            serverRef: server,
            terminalService: termSvc,
            idleTimeoutMinutes: idleTimeout,
            authService: authSvc,
            clientIP: clientIP
        )

        let wsUpgrade = self.wsUpgrade
        channel.eventLoop.execute {
            // Pause reads so incoming WS frames don't hit the HTTP decoder
            // during the pipeline swap (Safari sends the first WS frame
            // immediately after receiving 101).
            channel.setOption(ChannelOptions.autoRead, value: false).whenFailure { _ in }

            let responseHead = HTTPResponseHead(
                version: .http1_1, status: .switchingProtocols, headers: upgrade.headers
            )
            channel.write(self.wrapOutboundOut(.head(responseHead)), promise: nil)
            channel.writeAndFlush(self.wrapOutboundOut(.end(nil))).whenSuccess {
                wsUpgrade.installWebSocketPipeline(channel: channel, handler: wsHandler)
            }
        }
    }

    // MARK: - Channel Write

    /// Write a head + body pair to the channel and flush. Body of zero length
    /// is sent as the head + empty end frame (no body part).
    private func write(head: HTTPResponseHead, body: ByteBuffer, to channel: Channel) {
        channel.write(wrapOutboundOut(.head(head)), promise: nil)
        if body.readableBytes > 0 {
            channel.write(wrapOutboundOut(.body(.byteBuffer(body))), promise: nil)
        }
        channel.writeAndFlush(wrapOutboundOut(.end(nil)), promise: nil)
    }

    /// Run a MainActor builder closure, then dispatch the write back to the
    /// channel's event loop. Used for non-authenticated routes that touch
    /// MainActor state (health, auth/token, debug endpoints).
    private func dispatchMainActor(
        channel: Channel,
        builder: @escaping @MainActor (ByteBufferAllocator) -> (HTTPResponseHead, ByteBuffer)?
    ) {
        let alloc = channel.allocator
        Task { @MainActor [weak self] in
            guard let response = builder(alloc) else { return }
            channel.eventLoop.execute { [weak self] in
                self?.write(head: response.0, body: response.1, to: channel)
            }
        }
    }

    // MARK: - Parsing Helpers

    private func extractBearerToken(from head: HTTPRequestHead) -> String? {
        guard let auth = head.headers["Authorization"].first,
              auth.hasPrefix("Bearer ") else { return nil }
        return String(auth.dropFirst(7))
    }
}

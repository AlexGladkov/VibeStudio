// MARK: - HTTPRequestRouter
// ARCH-H1 shim. After the Wave-4 split, this file is intentionally a thin
// NIO `ChannelInboundHandler` that does only the things which require
// per-channel state: accumulate request parts, resolve a safe CORS origin,
// run auth on MainActor, and delegate to ``RemoteAPIRouter`` /
// ``RemoteAPIHandlers``. Static-file serving lives in
// ``RemoteStaticFileServer``; response building in ``HTTPResponseWriter``;
// auth helpers in ``RemoteAuthMiddleware``.
//
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
/// Runs on a NIO event-loop thread. Service reads that require `@MainActor`
/// hop via `Task { @MainActor in ... }` and write responses back through
/// `channel.eventLoop.execute`. `ChannelHandlerContext` is **not**
/// `Sendable` — never captured in `Task` closures.
final class HTTPRequestRouter: ChannelInboundHandler, RemovableChannelHandler {

    typealias InboundIn = HTTPServerRequestPart
    typealias OutboundOut = HTTPServerResponsePart

    // MARK: - Dependencies (thread-safe references)

    private let authService: RemoteAuthService
    private let terminalService: TerminalService
    private let projectManager: any ProjectManaging
    private let preferences: RemoteControlPreferences
    private let generalPreferences: GeneralPreferences
    private let cachedIdleTimeoutMinutes: Int
    private weak var serverRef: RemoteControlServer?

    /// Cached app metadata (Bundle.main snapshot from MainActor init).
    /// ARCH-H8: no Bundle.main access from NIO threads.
    private let metadata: RemoteServerMetadata

    /// Resolves the `RemoteControlWeb` bundle URL once at server start
    /// (ARCH-H8) and holds the pre-loaded asset cache.
    private let staticCache: RemoteStaticFileCache

    /// Stateless HTTP response builder shared across requests.
    private let writer: HTTPResponseWriter

    /// Static-file server (uses cached baseURL + writer).
    private let staticFileServer: RemoteStaticFileServer

    /// Pure path → handler dispatcher.
    private let apiRouter: RemoteAPIRouter

    /// Shared decoder for request bodies.
    private let decoder = JSONDecoder()

    /// Shared ISO-8601 formatter (expensive to allocate per request).
    private static let isoFormatter = ISO8601DateFormatter()

    /// Shared encoder configured for ISO-8601 dates.
    private static func makeEncoder() -> JSONEncoder {
        let enc = JSONEncoder()
        enc.dateEncodingStrategy = .iso8601
        return enc
    }

    /// Exact-host ngrok reference (read on NIO thread for CORS).
    private let ngrokHostRef: NgrokHostRef

    // MARK: - Per-channel state

    /// Real TCP-level remote address captured in `channelActive`.
    /// Never derived from spoofable headers.
    private var remoteAddress: String = "unknown"

    /// Real TCP-level client IP. X-Forwarded-For is intentionally NOT
    /// consulted — spoofable and must never feed rate limiting / token binding.
    private var currentClientIP: String { remoteAddress }

    // MARK: - Request accumulator

    private var requestHead: HTTPRequestHead?
    private var requestBody: ByteBuffer?

    // MARK: - DEBUG-only WS upgrade log
    #if DEBUG
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

    // MARK: - Init

    init(
        authService: RemoteAuthService,
        terminalService: TerminalService,
        projectManager: any ProjectManaging,
        preferences: RemoteControlPreferences,
        generalPreferences: GeneralPreferences,
        idleTimeoutMinutes: Int,
        serverRef: RemoteControlServer?,
        staticCache: RemoteStaticFileCache = .empty,
        ngrokHostRef: NgrokHostRef = NgrokHostRef(),
        metadata: RemoteServerMetadata = RemoteServerMetadata(appVersion: RemoteServerMetadata.unknownVersion)
    ) {
        self.authService = authService
        self.terminalService = terminalService
        self.projectManager = projectManager
        self.preferences = preferences
        self.generalPreferences = generalPreferences
        self.cachedIdleTimeoutMinutes = idleTimeoutMinutes
        self.serverRef = serverRef
        self.staticCache = staticCache
        self.ngrokHostRef = ngrokHostRef
        self.metadata = metadata

        let encoder = Self.makeEncoder()
        let writer = HTTPResponseWriter(encoder: encoder)
        self.writer = writer
        self.staticFileServer = RemoteStaticFileServer(cache: staticCache, writer: writer)

        let handlers = RemoteAPIHandlers(
            authService: authService,
            terminalService: terminalService,
            projectManager: projectManager,
            preferences: preferences,
            serverRef: serverRef,
            writer: writer,
            metadata: metadata
        )
        self.apiRouter = RemoteAPIRouter(
            handlers: handlers,
            decoder: decoder,
            isoFormatter: Self.isoFormatter
        )
    }

    // MARK: - ChannelInboundHandler

    func channelActive(context: ChannelHandlerContext) {
        if let remoteAddr = context.channel.remoteAddress {
            switch remoteAddr {
            case .v4(let addr): remoteAddress = addr.host
            case .v6(let addr): remoteAddress = addr.host
            default: remoteAddress = "unknown"
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
            // 64 KB body limit for API requests.
            if (requestBody?.readableBytes ?? 0) + body.readableBytes > 65_536 {
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
                    version: .http1_1, status: .payloadTooLarge, headers: headers
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
            HTTPRequestRouter.wsLog(
                "[ROUTER] \(method.rawValue) \(path) Upgrade=\(upgradeHeader) Connection=\(connHeader) ip=\(self.remoteAddress)"
            )
        }
        #endif

        // ARCH-H9: resolve CORS origin once per request and pass it through
        // every send* call. Never store on the handler — keep-alive sockets
        // would otherwise risk reading the next request's origin from a
        // Task-deferred response.
        let corsOrigin = allowedOrigin(from: head)

        // CORS preflight.
        if method == .OPTIONS {
            writer.sendCORSPreflight(channel: channel, corsOrigin: corsOrigin)
            return
        }

        // Static files (no auth).
        if method == .GET {
            switch path {
            case "/":
                staticFileServer.serveStaticFile(
                    "index.html", contentType: "text/html",
                    channel: channel, corsOrigin: corsOrigin
                )
                return
            case "/app.css":
                staticFileServer.serveStaticFile(
                    "app.css", contentType: "text/css",
                    channel: channel, corsOrigin: corsOrigin
                )
                return
            case "/app.js":
                staticFileServer.serveStaticFile(
                    "app.js", contentType: "application/javascript",
                    channel: channel, corsOrigin: corsOrigin
                )
                return
            case "/favicon.ico":
                staticFileServer.serveStaticFile(
                    "favicon.ico", contentType: "image/x-icon",
                    channel: channel, corsOrigin: corsOrigin
                )
                return
            default:
                if path.hasPrefix("/vendor/") {
                    let fileName = String(path.dropFirst(1)) // "vendor/..."
                    let ext = (fileName as NSString).pathExtension
                    let mime = MIMETypeResolver.mimeType(for: ext)
                    staticFileServer.serveStaticFile(
                        fileName, contentType: mime,
                        channel: channel, corsOrigin: corsOrigin
                    )
                    return
                }
            }
        }

        // Health (no auth).
        if method == .GET && path == "/api/v1/health" {
            let theWriter = writer
            Task { @MainActor in
                theWriter.sendRawJSON(
                    status: .ok,
                    data: Data(#"{"status":"healthy"}"#.utf8),
                    channel: channel,
                    corsOrigin: corsOrigin
                )
            }
            return
        }

        // Auth/token (no bearer required).
        if method == .POST && path == "/api/v1/auth/token" {
            let handlers = apiRouter.handlers
            let clientIP = currentClientIP
            let remoteAddr = remoteAddress
            let decoder = self.decoder
            Task { @MainActor in
                handlers.handleAuthToken(
                    head: head,
                    body: body,
                    channel: channel,
                    clientIP: clientIP,
                    remoteAddress: remoteAddr,
                    corsOrigin: corsOrigin,
                    decoder: decoder
                )
            }
            return
        }

        #if DEBUG
        // Debug endpoints gated behind VS_DEBUG_API=1.
        let debugAPIEnabled = ProcessInfo.processInfo.environment["VS_DEBUG_API"] != nil

        if method == .GET && path == "/api/v1/debug/pin" {
            guard debugAPIEnabled else {
                writer.sendErrorJSON(
                    status: .notFound, code: "NOT_FOUND",
                    message: "The requested resource was not found.",
                    channel: channel, corsOrigin: corsOrigin
                )
                return
            }
            let authSvc = authService
            let theWriter = writer
            Task { @MainActor in
                let pin = authSvc.currentPin
                let json = "{\"pin\":\"\(pin)\"}"
                channel.eventLoop.execute {
                    theWriter.sendRawJSON(
                        status: .ok, data: Data(json.utf8),
                        channel: channel, corsOrigin: corsOrigin
                    )
                }
            }
            return
        }
        if method == .GET && path == "/api/v1/debug/wslog" {
            guard debugAPIEnabled else {
                writer.sendErrorJSON(
                    status: .notFound, code: "NOT_FOUND",
                    message: "The requested resource was not found.",
                    channel: channel, corsOrigin: corsOrigin
                )
                return
            }
            let log = HTTPRequestRouter.wsDebugLog
            let json = "[" + log.map { "\"\($0.replacingOccurrences(of: "\"", with: "\\\""))\"" }
                .joined(separator: ",") + "]"
            writer.sendRawJSON(
                status: .ok, data: Data(json.utf8),
                channel: channel, corsOrigin: corsOrigin
            )
            return
        }
        if method == .GET && path == "/api/v1/debug/state" {
            guard debugAPIEnabled else {
                writer.sendErrorJSON(
                    status: .notFound, code: "NOT_FOUND",
                    message: "The requested resource was not found.",
                    channel: channel, corsOrigin: corsOrigin
                )
                return
            }
            let authSvc = authService
            let callerIP = remoteAddress
            let serverRefLocal = self.serverRef
            let theWriter = writer
            Task { @MainActor in
                let devices = authSvc.connectedDevices.map { d in
                    "{\"id\":\"\(d.id)\",\"ip\":\"\(d.ipAddress)\"}"
                }.joined(separator: ",")
                let deviceCount = authSvc.connectedDevices.count
                let ngrokURL = serverRefLocal?.ngrokTunnelURL ?? "null"
                let ngrokRunning = serverRefLocal?.isNgrokRunning ?? false
                let ngrokError = serverRefLocal?.ngrokError ?? "null"
                let json = """
                {"caller_ip":"\(callerIP)","devices":[\(devices)],"device_count":\(deviceCount),\
                "ngrok":{"running":\(ngrokRunning),"url":"\(ngrokURL)","error":"\(ngrokError)"}}
                """
                channel.eventLoop.execute {
                    theWriter.sendRawJSON(
                        status: .ok, data: Data(json.utf8),
                        channel: channel, corsOrigin: corsOrigin
                    )
                }
            }
            return
        }
        #endif

        // WebSocket upgrade for terminal endpoints (auth happens on first WS frame).
        if method == .GET && path.hasPrefix("/api/v1/terminal/") &&
           head.headers["Upgrade"].first?.caseInsensitiveCompare("websocket") == .orderedSame {
            handleWebSocketUpgrade(head: head, path: path, channel: channel, corsOrigin: corsOrigin)
            return
        }

        // All other /api/ endpoints require Bearer auth.
        if path.hasPrefix("/api/") {
            let clientIP = currentClientIP
            guard let token = RemoteAuthMiddleware.extractBearerToken(from: head) else {
                writer.sendErrorJSON(
                    status: .unauthorized,
                    code: "AUTH_REQUIRED",
                    message: "Authorization header with Bearer token is required.",
                    channel: channel,
                    corsOrigin: corsOrigin
                )
                return
            }

            let authSvc = authService
            let apiRouter = self.apiRouter
            let theWriter = writer

            Task { @MainActor in
                let result = authSvc.validateToken(token, clientIP: clientIP)
                switch result {
                case .failure(let error):
                    let (status, code, message, retryAfter) =
                        RemoteAuthMiddleware.authErrorResponse(error)
                    let resp = ErrorResponse(error: ErrorDetail(code: code, message: message))
                    let data = (try? theWriter.encoder.encode(resp)) ?? Data()
                    channel.eventLoop.execute {
                        theWriter.sendRawJSON(
                            status: status,
                            data: data,
                            channel: channel,
                            corsOrigin: corsOrigin,
                            retryAfterSeconds: retryAfter
                        )
                    }

                case .success(let device):
                    apiRouter.routeAuthenticated(
                        path: path,
                        method: method,
                        head: head,
                        body: body,
                        device: device,
                        channel: channel,
                        corsOrigin: corsOrigin
                    )
                }
            }
            return
        }

        writer.sendErrorJSON(
            status: .notFound,
            code: "NOT_FOUND",
            message: "The requested resource was not found.",
            channel: channel,
            corsOrigin: corsOrigin
        )
    }

    // MARK: - WebSocket Upgrade (RFC 6455)

    /// Perform a manual WebSocket upgrade. NIO's `HTTPServerUpgradeHandler`
    /// is one-shot and removes itself after the first non-upgrade request,
    /// breaking HTTP/1.1 keep-alive connections where the WS upgrade comes
    /// after page load. This method handles the upgrade directly.
    private func handleWebSocketUpgrade(
        head: HTTPRequestHead,
        path: String,
        channel: Channel,
        corsOrigin: String?
    ) {
        let sessionIdStr = String(path.dropFirst("/api/v1/terminal/".count))
        guard let sessionId = UUID(uuidString: sessionIdStr) else {
            writer.sendErrorJSON(
                status: .badRequest, code: "INVALID_SESSION",
                message: "Invalid session ID.",
                channel: channel, corsOrigin: corsOrigin
            )
            return
        }

        // SEC-H1: reject WS upgrade when at device cap. We read
        // `activeBridges.count` via the server reference; if no server is
        // wired in (preview/test) we skip the check.
        if let server = serverRef {
            // serverRef is @MainActor — hop briefly to read state, then bounce
            // back to the event loop for the upgrade.
            let theWriter = writer
            Task { @MainActor in
                let atCapacity = server.activeBridges.count >= RemoteAuthService.maxDevices
                if atCapacity {
                    channel.eventLoop.execute {
                        theWriter.sendErrorJSON(
                            status: .serviceUnavailable,
                            code: "MAX_DEVICES_REACHED",
                            message: "Maximum number of connected devices reached. Try again later.",
                            channel: channel,
                            corsOrigin: corsOrigin
                        )
                    }
                    return
                }
                channel.eventLoop.execute { [weak self] in
                    self?.performWebSocketUpgrade(
                        head: head, sessionId: sessionId, channel: channel, corsOrigin: corsOrigin
                    )
                }
            }
            return
        }

        performWebSocketUpgrade(
            head: head, sessionId: sessionId, channel: channel, corsOrigin: corsOrigin
        )
    }

    private func performWebSocketUpgrade(
        head: HTTPRequestHead,
        sessionId: UUID,
        channel: Channel,
        corsOrigin: String?
    ) {
        guard let wsKey = head.headers["Sec-WebSocket-Key"].first else {
            writer.sendErrorJSON(
                status: .badRequest, code: "INVALID_REQUEST",
                message: "Missing Sec-WebSocket-Key.",
                channel: channel, corsOrigin: corsOrigin
            )
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

        // Sec-WebSocket-Accept per RFC 6455 §4.2.2.
        let magicGUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        let hash = Insecure.SHA1.hash(data: Data((wsKey + magicGUID).utf8))
        let acceptValue = Data(hash).base64EncodedString()

        let wsHandler = RemoteWebSocketHandler(
            sessionId: sessionId,
            deviceInfo: nil, // auth on first WS frame
            serverRef: server,
            terminalService: termSvc,
            idleTimeoutMinutes: idleTimeout,
            authService: authSvc,
            clientIP: clientIP
        )

        channel.eventLoop.execute {
            // Pause reads so incoming WS frames don't hit the HTTP decoder
            // during the pipeline swap.
            channel.setOption(ChannelOptions.autoRead, value: false).whenFailure { _ in }

            var headers = HTTPHeaders()
            headers.add(name: "Upgrade", value: "websocket")
            headers.add(name: "Connection", value: "upgrade")
            headers.add(name: "Sec-WebSocket-Accept", value: acceptValue)
            // Only echo "vibestudio.v1" back — never arbitrary client-supplied protocol.
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

    /// Replace the HTTP pipeline with the WebSocket frame codec + handler.
    private func installWebSocketPipeline(channel: Channel, handler: RemoteWebSocketHandler) {
        #if DEBUG
        HTTPRequestRouter.wsLog("[WS] installWebSocketPipeline: starting pipeline swap")
        #endif

        let el = channel.eventLoop
        let pipeline = channel.pipeline

        func removeByName(_ name: String) -> EventLoopFuture<Void> {
            pipeline.removeHandler(name: name).flatMapError { _ in
                el.makeSucceededVoidFuture()
            }
        }

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

    // MARK: - CORS

    /// Build a safe CORS origin value. Reflects the Origin header only when
    /// it matches `localhost` / `127.0.0.1` / `[::1]` on any port, or the
    /// exact current ngrok host. Wildcards are intentionally rejected.
    private func allowedOrigin(from head: HTTPRequestHead) -> String? {
        guard let origin = head.headers["Origin"].first,
              let url = URL(string: origin),
              let host = url.host else {
            return nil
        }
        let allowedHosts: Set<String> = ["localhost", "127.0.0.1", "[::1]"]
        if allowedHosts.contains(host) { return origin }
        if let ngrokHost = ngrokHostRef.host, host == ngrokHost {
            return origin
        }
        return nil
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

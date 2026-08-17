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

import Foundation
import NIOCore
import NIOHTTP1
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

    let authService: RemoteAuthService
    private let terminalService: TerminalService
    private let projectManager: any ProjectManaging
    private let preferences: RemoteControlPreferences
    private weak var serverRef: RemoteControlServer?
    /// Optional cost tracker — forwarded to ``RemoteAPIHandlers`` for reconnect recovery.
    private weak var costTrackerService: CostTrackerService?

    /// Cached app metadata (Bundle.main snapshot from MainActor init).
    /// ARCH-H8: no Bundle.main access from NIO threads.
    private let metadata: RemoteServerMetadata

    /// Resolves the `RemoteControlWeb` bundle URL once at server start
    /// (ARCH-H8) and holds the pre-loaded asset cache.
    private let staticCache: RemoteStaticFileCache

    /// Stateless HTTP response builder shared across requests.
    let writer: HTTPResponseWriter

    /// Static-file server (uses cached baseURL + writer).
    private let staticFileServer: RemoteStaticFileServer

    /// Pure path → handler dispatcher.
    let apiRouter: RemoteAPIRouter

    /// Shared decoder for request bodies.
    private let decoder = JSONDecoder()

    // MARK: - Sub-routers (Wave-6 split)

    /// Static-file + `/api/v1/health` routes.
    private let staticRouter: StaticFileRouter

    /// Public `/api/v1/auth/*` routes that do NOT need a Bearer token.
    private let authRouter: AuthEndpointRouter

    #if DEBUG
    /// `/api/v1/debug/*` routes. Compiled out in Release.
    private let debugRouter: DebugEndpointRouter
    #endif

    /// Cached, flattened table of every route claimed by the sub-routers.
    /// Built lazily on first use — `routes()` only runs once per channel
    /// handler instance.
    lazy var preAuthRoutes: [RouteSpec] = {
        var routes: [RouteSpec] = []
        routes.append(contentsOf: staticRouter.routes())
        routes.append(contentsOf: authRouter.routes())
        #if DEBUG
        routes.append(contentsOf: debugRouter.routes())
        #endif
        return routes
    }()

    /// Shared ISO-8601 formatter (expensive to allocate per request).
    private static let isoFormatter = ISO8601DateFormatter()

    /// Shared encoder configured for ISO-8601 dates.
    static func makeEncoder() -> JSONEncoder {
        let enc = JSONEncoder()
        enc.dateEncodingStrategy = .iso8601
        return enc
    }

    /// CORS origin policy (A7 split) — allow-list + reflect-origin logic.
    let corsPolicy: CORSPolicy

    /// Manual RFC 6455 WebSocket upgrade coordinator (A7 split). Owned
    /// strongly here so `[weak self]` inside its deferred closures mirrors
    /// the original per-channel-handler teardown semantics.
    let wsUpgrader: WebSocketUpgradeHandler

    // MARK: - Per-channel state

    /// Real TCP-level remote address captured in `channelActive`.
    /// Never derived from spoofable headers.
    var remoteAddress: String = "unknown"

    /// Real TCP-level client IP. X-Forwarded-For is intentionally NOT
    /// consulted — spoofable and must never feed rate limiting / token binding.
    var currentClientIP: String { remoteAddress }

    // MARK: - Request accumulator

    private var requestHead: HTTPRequestHead?
    private var requestBody: ByteBuffer?

    // MARK: - Developer-build detection (SEC-M1)

    #if DEBUG
    /// `true` only when this binary was loaded from a developer build
    /// directory (Xcode `DerivedData`, an `xcodebuild` build folder, or a
    /// `.build` SwiftPM tree). For a packaged TestFlight / Ad-Hoc / Release
    /// build the bundle lives in `/Applications/...`, `~/Applications/...`
    /// or a sandbox container and the check evaluates to `false`. The
    /// result is cached at first access because the bundle path is fixed
    /// for the lifetime of the process.
    private static let isDeveloperBuild: Bool = {
        let bundlePath = Bundle.main.bundlePath
        let markers = ["/DerivedData/", "/.build/", "/Build/Products/", "/build/derivedData/"]
        return markers.contains { bundlePath.contains($0) }
    }()
    #endif

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
        idleTimeoutMinutes: Int,
        serverRef: RemoteControlServer?,
        staticCache: RemoteStaticFileCache = .empty,
        ngrokHostRef: NgrokHostRef = NgrokHostRef(),
        metadata: RemoteServerMetadata = RemoteServerMetadata(appVersion: RemoteServerMetadata.unknownVersion),
        costTrackerService: CostTrackerService? = nil
    ) {
        self.authService = authService
        self.terminalService = terminalService
        self.projectManager = projectManager
        self.preferences = preferences
        self.serverRef = serverRef
        self.staticCache = staticCache
        self.corsPolicy = CORSPolicy(ngrokHostRef: ngrokHostRef)
        self.metadata = metadata
        self.costTrackerService = costTrackerService

        // All immutable collaborators are wired up in one factory (see
        // ``HTTPRequestRouter+Setup``) to keep this initializer readable.
        let parts = Collaborators(
            authService: authService, terminalService: terminalService,
            projectManager: projectManager, preferences: preferences,
            serverRef: serverRef, staticCache: staticCache, metadata: metadata,
            idleTimeoutMinutes: idleTimeoutMinutes, decoder: decoder,
            isoFormatter: Self.isoFormatter,
            costTrackerService: costTrackerService
        )
        self.writer = parts.writer
        self.staticFileServer = parts.staticFileServer
        self.wsUpgrader = parts.wsUpgrader
        self.apiRouter = parts.apiRouter
        self.staticRouter = parts.staticRouter
        self.authRouter = parts.authRouter
        #if DEBUG
        self.debugRouter = DebugEndpointRouter(
            authService: authService, serverRef: serverRef,
            writer: parts.writer, isEnabled: HTTPRequestRouter.isDeveloperBuild
        )
        #endif
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
            if (requestBody?.readableBytes ?? 0) + body.readableBytes > RemoteLimits.maxRequestBodyBytes {
                requestHead = nil
                requestBody = nil
                let channel = context.channel
                // Route through the shared writer so the 413 carries the same
                // security headers + API-Version as every other response.
                // `sendErrorJSON` writes head/body/end and flushes; closing the
                // channel afterwards (on the same event loop) fires only after
                // the flushed response drains, mirroring the previous
                // Connection: close teardown.
                writer.sendErrorJSON(
                    status: .payloadTooLarge,
                    code: "PAYLOAD_TOO_LARGE",
                    message: "Request body exceeds the 64 KB limit.",
                    channel: channel,
                    corsOrigin: nil
                )
                channel.close(promise: nil)
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
}

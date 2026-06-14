// MARK: - RemoteControlServer
// Main @Observable facade for the embedded HTTP/WebSocket server.
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOPosix
import NIOHTTP1
import NIOWebSocket
import NIOSSL
import Observation
import OSLog
import UserNotifications

// WebSocket upgrade is handled manually in HTTPRequestRouter (not via NIO's
// HTTPServerUpgradeHandler, which is one-shot and breaks on HTTP/1.1 keep-alive).

/// Embedded HTTPS + WebSocket server for remote terminal control.
///
/// This is the top-level facade observed by SwiftUI (toolbar badge, settings pane).
/// It owns the SwiftNIO `EventLoopGroup`, manages the TLS bootstrap, and tracks
/// connected devices via ``RemoteAuthService``.
///
/// **Threading model:**
/// - All public API is `@MainActor` (SwiftUI-safe).
/// - NIO runs on its own `MultiThreadedEventLoopGroup` threads.
/// - `start()` / `stop()` use `Task.detached` for NIO bootstrap, then
///   hop back to MainActor for state updates.
/// - `Channel` references are `nonisolated(unsafe)` because `Channel` is
///   `Sendable` but not annotated as `@MainActor`.
@Observable
@MainActor
final class RemoteControlServer {

    // MARK: - Observable State

    /// Whether the NIO server is currently listening.
    private(set) var isRunning: Bool = false

    /// Guards against overlapping start/stop transitions.
    ///
    /// Set to `true` before any async lifecycle task is dispatched and
    /// cleared (on MainActor) once the transition completes or fails.
    /// This prevents a second `start()` from racing against an in-flight
    /// `stop()` shutdown and vice-versa, which would otherwise cause a
    /// double-bind on the same port.
    private var isTransitioning: Bool = false

    /// Number of devices with active WebSocket connections.
    private(set) var connectedDeviceCount: Int = 0

    /// Last startup failure message, surfaced in UI. Cleared on successful start.
    private(set) var startupError: String?

    /// The current 6-digit PIN for device authentication.
    var currentPin: String { authService.currentPin }

    /// All authenticated and connected remote devices.
    var connectedDevices: [RemoteDevice] { authService.connectedDevices }

    /// The TCP port the server is bound to.
    var port: Int { preferences.remoteControlPort }

    /// The public ngrok tunnel URL, if active.
    var ngrokTunnelURL: String? { ngrok.tunnelURL }

    /// Whether the ngrok tunnel is currently running.
    var isNgrokRunning: Bool { ngrok.isRunning }

    /// Ngrok error message, if any.
    var ngrokError: String? { ngrok.error }

    /// Whether the server is in global lockout mode.
    var isLocked: Bool { authService.isLocked }

    // MARK: - Dependencies

    private let authService: RemoteAuthService
    private let preferences: RemoteControlPreferences
    private let terminalService: TerminalService
    private let projectManager: any ProjectManaging
    /// Resolver for Claude `--dangerously-skip-permissions` — passed to every router.
    private let claudePermissions: any ClaudePermissionsResolving
    private let bonjour: BonjourAdvertiser
    private let ngrok: NgrokTunnelService
    /// Backing store for remote chat attachments (image uploads).
    private let uploadStore: RemoteUploadStore

    // MARK: - NIO State

    /// All NIO state is only ever mutated on the MainActor:
    /// - Written in `start()` via `await MainActor.run { ... }`
    /// - Written in `stop()` which is itself `@MainActor`
    /// - Read in `stop()` before handing off to `Task.detached`
    /// No cross-thread mutation occurs, so `nonisolated(unsafe)` is not needed.
    private var eventLoopGroup: MultiThreadedEventLoopGroup?

    /// The bound server channel (HTTPS).
    private var serverChannel: Channel?

    /// Plain HTTP channel for LAN access (port + 1). Avoids iOS Safari
    /// self-signed cert issues with WebSocket.
    private var httpChannel: Channel?

    // MARK: - Active Bridges

    /// Active session bridges keyed by device ID.
    private(set) var activeBridges: [UUID: RemoteSessionBridge] = [:]

    // MARK: - Server Metadata

    /// Timestamp when the server was started (for uptime calculation).
    private var startedAt: Date?

    /// Observation task for session changes broadcast.
    ///
    /// Written only in init/start (MainActor). Read from nonisolated `deinit`
    /// to call `.cancel()` — `Task` is `Sendable` and `cancel()` is atomic, so
    /// `nonisolated(unsafe)` is required by Swift 5.10 strict concurrency.
    nonisolated(unsafe) private var sessionObservationTask: Task<Void, Never>?

    /// See `sessionObservationTask` for isolation rationale.
    nonisolated(unsafe) private var ngrokHostObservationTask: Task<Void, Never>?

    /// Shared ngrok host reference passed to all HTTPRequestRouter instances.
    /// Updated on MainActor when the ngrok tunnel URL is resolved; read on
    /// NIO event loop threads inside the router (safe because NgrokHostRef
    /// is a class with its own internal lock).
    private let ngrokHostRef = NgrokHostRef()

    /// Uptime in seconds since server start.
    var uptimeSeconds: Int {
        guard let startedAt else { return 0 }
        return Int(Date().timeIntervalSince(startedAt))
    }

    // MARK: - Init

    /// Create a new Remote Control server.
    ///
    /// - Parameters:
    ///   - authService: PIN/token authentication service.
    ///   - preferences: User preferences for port, binding, timeouts.
    ///   - terminalService: Terminal PTY service for I/O relay.
    ///   - projectManager: Project list for the REST API.
    init(
        authService: RemoteAuthService,
        preferences: RemoteControlPreferences,
        terminalService: TerminalService,
        projectManager: any ProjectManaging,
        claudePermissions: any ClaudePermissionsResolving
    ) {
        self.authService = authService
        self.preferences = preferences
        self.terminalService = terminalService
        self.projectManager = projectManager
        self.claudePermissions = claudePermissions
        self.bonjour = BonjourAdvertiser()
        self.ngrok = NgrokTunnelService()
        self.uploadStore = RemoteUploadStore()
    }

    /// Preview/EnvironmentKey-default factory.
    ///
    /// Builds a server using stub dependencies that never trigger:
    /// - `DistributedNotificationCenter` observer registration (`ThemeService.previewStub`)
    /// - `UserDefaults` writes (`GeneralPreferences.previewStub`, `RemoteControlPreferences.previewStub`)
    ///
    /// The returned server is NEVER started — it exists only to satisfy SwiftUI
    /// `EnvironmentKey.defaultValue` so previews don't crash on missing injection.
    static func previewStub() -> RemoteControlServer {
        let prefs = RemoteControlPreferences.previewStub()
        let theme = ThemeService.previewStub()
        let general = GeneralPreferences.previewStub()
        let terminal = TerminalService(themeService: theme, generalPreferences: general)
        return RemoteControlServer(
            authService: RemoteAuthService(),
            preferences: prefs,
            terminalService: terminal,
            projectManager: PreviewProjectManagerStub(),
            claudePermissions: general
        )
    }

    deinit {
        // Capture to locals to call `.cancel()` from nonisolated deinit.
        // `Task` is Sendable and `cancel()` is atomic — safe across actors.
        let sessionTask = sessionObservationTask
        let ngrokTask = ngrokHostObservationTask
        sessionTask?.cancel()
        ngrokTask?.cancel()
    }

    // MARK: - Server Lifecycle

    /// Start the HTTPS/WSS server.
    ///
    /// Creates a 2-thread `MultiThreadedEventLoopGroup`, configures TLS with
    /// a self-signed certificate from ``TLSCertificateManager``, and binds
    /// to the configured port.
    func start() {
        guard !isRunning, !isTransitioning else {
            Logger.remoteControl.warning("RemoteControlServer.start() called while already running or transitioning")
            return
        }

        let bindHost = preferences.bindToLocalhost ? "127.0.0.1" : "0.0.0.0"
        let bindPort = preferences.remoteControlPort

        // Capture references for the detached task (Sendable boundary).
        let authSvc = self.authService
        let termSvc = self.terminalService
        let projMgr = self.projectManager
        let prefs = self.preferences
        let idleTimeout = self.preferences.idleTimeoutMinutes
        let claudePerms = self.claudePermissions
        let upStore = self.uploadStore

        isTransitioning = true
        startupError = nil
        weak var weakSelf = self

        // Pre-load all static files from the bundle on MainActor (safe to access
        // Bundle on main thread) before entering the detached Task. This avoids
        // disk I/O on NIO event loop threads for every HTTP request.
        let staticFileCache = Self.loadStaticFileCache()

        // Capture the shared ngrok host reference so all routers can read it.
        let ngrokRef = ngrokHostRef

        Task.detached {
            do {
                // Ensure TLS certificate exists.
                let (certPath, keyPath) = try TLSCertificateManager.ensureCertificate()

                // Configure NIO SSL.
                let certChain = try NIOSSLCertificate.fromPEMFile(certPath.path)
                let privateKey = try NIOSSLPrivateKey(file: keyPath.path, format: .pem)
                var tlsConfig = TLSConfiguration.makeServerConfiguration(
                    certificateChain: certChain.map { .certificate($0) },
                    privateKey: .privateKey(privateKey)
                )
                tlsConfig.certificateVerification = .none

                let sslContext = try NIOSSLContext(configuration: tlsConfig)

                let group = MultiThreadedEventLoopGroup(numberOfThreads: 2)

                // Shared child channel initializer: HTTP pipeline + router.
                // WebSocket upgrade is handled manually inside HTTPRequestRouter
                // (NIO's HTTPServerUpgradeHandler is one-shot and breaks on keep-alive).
                let configureChildChannel: @Sendable (Channel) -> EventLoopFuture<Void> = { channel in
                    // Manual HTTP pipeline setup with named handlers so that
                    // installWebSocketPipeline can remove them by name reliably.
                    // (configureHTTPServerPipeline uses unnamed handlers that
                    // pipeline.handler(type:) sometimes fails to find.)
                    let encoder = HTTPResponseEncoder()
                    let decoder = ByteToMessageHandler(
                        HTTPRequestDecoder(leftOverBytesStrategy: .forwardBytes)
                    )
                    let errorHandler = HTTPServerProtocolErrorHandler()
                    let router = HTTPRequestRouter(
                        authService: authSvc,
                        terminalService: termSvc,
                        projectManager: projMgr,
                        preferences: prefs,
                        idleTimeoutMinutes: idleTimeout,
                        serverRef: weakSelf,
                        staticFileCache: staticFileCache,
                        ngrokHostRef: ngrokRef,
                        claudePermissions: claudePerms,
                        uploadStore: upStore
                    )
                    return channel.pipeline.addHandler(encoder, name: "http_encoder").flatMap {
                        channel.pipeline.addHandler(decoder, name: "http_decoder")
                    }.flatMap {
                        channel.pipeline.addHandler(errorHandler, name: "http_error")
                    }.flatMap {
                        channel.pipeline.addHandler(router, name: "http_router")
                    }
                }

                let bootstrap = ServerBootstrap(group: group)
                    .serverChannelOption(ChannelOptions.backlog, value: 16)
                    .serverChannelOption(
                        ChannelOptions.socketOption(.so_reuseaddr),
                        value: 1
                    )
                    .childChannelInitializer { channel in
                        let sslHandler = NIOSSLServerHandler(context: sslContext)
                        return channel.pipeline.addHandler(sslHandler).flatMap {
                            configureChildChannel(channel)
                        }
                    }

                let channel = try await bootstrap.bind(host: bindHost, port: bindPort).get()

                // Plain HTTP listener on port+1 for iOS Safari (WSS + self-signed cert issue).
                let httpBootstrap = ServerBootstrap(group: group)
                    .serverChannelOption(ChannelOptions.backlog, value: 16)
                    .serverChannelOption(
                        ChannelOptions.socketOption(.so_reuseaddr),
                        value: 1
                    )
                    .childChannelInitializer(configureChildChannel)

                // SECURITY: Plain HTTP always bound to loopback only, regardless of
                // bindHost setting. This prevents cleartext credentials on the LAN.
                let httpCh = try? await httpBootstrap.bind(host: "127.0.0.1", port: bindPort + 1).get()

                await MainActor.run {
                    guard let server = weakSelf else { return }
                    server.eventLoopGroup = group
                    server.serverChannel = channel
                    server.httpChannel = httpCh
                    server.isRunning = true
                    server.isTransitioning = false
                    server.startedAt = Date()
                    server.startSessionObservation()
                    server.startNgrokHostObservation()
                    server.authService.onSecurityLockout = { [weak server] in
                        server?.handleSecurityLockout()
                    }
                    server.authService.onDevicesChanged = { [weak server] count in
                        server?.connectedDeviceCount = count
                    }

                    if server.preferences.bonjourEnabled && !server.preferences.bindToLocalhost {
                        server.bonjour.publish(port: bindPort)
                    }

                    if server.preferences.ngrokEnabled && !server.preferences.ngrokAuthtoken.isEmpty {
                        server.ngrok.start(
                            httpPort: bindPort,
                            authtoken: server.preferences.ngrokAuthtoken
                        )
                    }

                    Logger.remoteControl.info(
                        "RemoteControlServer started on \(bindHost, privacy: .public):\(bindPort)"
                    )
                }
            } catch {
                Logger.remoteControl.error(
                    "RemoteControlServer failed to start: \(error.localizedDescription, privacy: .public)"
                )
                let raw = error.localizedDescription
                let friendly: String
                if raw.contains("Address already in use") || raw.contains("bind") {
                    friendly = "Порт \(bindPort) уже занят. Закрой другую копию VibeStudio или поменяй порт в настройках."
                } else if raw.contains("Permission denied") {
                    friendly = "Нет доступа к порту \(bindPort). Используй порт > 1024."
                } else {
                    friendly = "Не удалось запустить Remote Control: \(raw)"
                }
                await MainActor.run {
                    weakSelf?.isTransitioning = false
                    weakSelf?.startupError = friendly
                }
            }
        }
    }

    /// Gracefully stop the server and disconnect all devices (fire-and-forget).
    ///
    /// Dispatches NIO shutdown to a detached task and returns immediately.
    /// Use ``stopAsync()`` when you need to await full shutdown (e.g. on app quit).
    func stop() {
        guard isRunning, !isTransitioning else { return }

        isTransitioning = true
        let (channel, httpCh, group) = beginStopCleanup()
        weak var weakSelf = self

        Task.detached {
            await Self.shutdownNIO(channel: channel, httpChannel: httpCh, group: group)
            Logger.remoteControl.info("RemoteControlServer stopped")
            await MainActor.run {
                weakSelf?.isTransitioning = false
            }
        }
    }

    /// Gracefully stop the server and await full NIO shutdown.
    ///
    /// Use this in ``AppLifecycleCoordinator`` on app termination so that NIO
    /// event loop threads are fully joined before the process exits. Returning
    /// early from `applicationShouldTerminate` with pending async NIO teardown
    /// can leave threads dangling and cause crashes on exit.
    func stopAsync() async {
        guard isRunning, !isTransitioning else { return }

        isTransitioning = true
        let (channel, httpCh, group) = beginStopCleanup()

        await Self.shutdownNIO(channel: channel, httpChannel: httpCh, group: group)
        Logger.remoteControl.info("RemoteControlServer stopped (async)")
        isTransitioning = false
    }

    /// Shared synchronous portion of stop: detaches bridges, resets observable
    /// state, and returns the NIO handles for async shutdown.
    /// Must be called on MainActor.
    @discardableResult
    private func beginStopCleanup() -> (Channel?, Channel?, MultiThreadedEventLoopGroup?) {
        // Detach all bridges.
        for (_, bridge) in activeBridges {
            bridge.detach()
        }
        activeBridges.removeAll()
        connectedDeviceCount = 0

        bonjour.unpublish()
        ngrok.stop()
        ngrokHostRef.set(nil)
        // NOTE: tokens are deliberately NOT revoked here. `stop()` also runs on
        // benign restarts (port change, app quit); wiping tokens on those forced
        // a PIN re-prompt the user can't satisfy remotely. Tokens persist
        // (Keychain) and survive the restart. Explicit revocation happens only
        // when the user turns Remote Control OFF — see `RemoteControlSettingsPane`.

        sessionObservationTask?.cancel()
        sessionObservationTask = nil
        ngrokHostObservationTask?.cancel()
        ngrokHostObservationTask = nil

        let channel = serverChannel
        let httpCh = httpChannel
        let group = eventLoopGroup

        isRunning = false
        startedAt = nil
        serverChannel = nil
        httpChannel = nil
        eventLoopGroup = nil

        return (channel, httpCh, group)
    }

    /// Await closure of NIO channels and event loop group shutdown.
    /// `nonisolated` so it can be called from both `stop()` Task.detached and `stopAsync()`.
    private static func shutdownNIO(
        channel: Channel?,
        httpChannel: Channel?,
        group: MultiThreadedEventLoopGroup?
    ) async {
        if let channel {
            try? await channel.close().get()
        }
        if let httpChannel {
            try? await httpChannel.close().get()
        }
        if let group {
            try? await group.shutdownGracefully()
        }
    }

    // MARK: - PIN Management

    /// Start the ngrok tunnel (called from settings toggle while server is running).
    func startNgrok() {
        guard isRunning else { return }
        ngrok.start(httpPort: preferences.remoteControlPort, authtoken: preferences.ngrokAuthtoken)
    }

    /// Stop the ngrok tunnel.
    func stopNgrok() {
        ngrok.stop()
    }

    /// Regenerate the authentication PIN.
    func regeneratePin() {
        authService.regeneratePin()
    }

    /// Revoke all authenticated devices and clear persisted tokens.
    /// Called when the user explicitly turns Remote Control OFF (a security
    /// action), as opposed to a benign restart which preserves tokens.
    func revokeAllDevices() {
        authService.revokeAllDevices()
    }

    // MARK: - Security Lockout

    /// Handle global lockout: send notification and stop server.
    private func handleSecurityLockout() {
        Logger.remoteControl.error("Security lockout triggered — stopping Remote Control server")

        let content = UNMutableNotificationContent()
        content.title = "Remote Control отключён"
        content.body = "Обнаружено 10 неудачных попыток входа. Сервер остановлен в целях безопасности."
        content.sound = .default

        let request = UNNotificationRequest(
            identifier: "rc-security-lockout-\(UUID().uuidString)",
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request) { error in
            if let error {
                Logger.remoteControl.error("Failed to deliver lockout notification: \(error.localizedDescription)")
            }
        }

        stop()
    }

    // MARK: - Device Management

    /// Disconnect a specific remote device.
    ///
    /// - Parameter deviceId: The device to disconnect.
    func disconnect(_ deviceId: UUID) {
        if let bridge = activeBridges[deviceId] {
            bridge.detach()
            activeBridges.removeValue(forKey: deviceId)
        }
        authService.revokeDevice(deviceId)
        connectedDeviceCount = activeBridges.count
        RemoteAuditLog.deviceDisconnect(deviceId: deviceId, reason: "kicked_by_host")
    }

    // MARK: - Bridge Registration

    /// Register a new session bridge (called from NIO handlers via MainActor hop).
    ///
    /// Installs `onLinesChanged` callback on the terminal view so that
    /// terminal output is relayed to the WebSocket client.
    func registerBridge(_ bridge: RemoteSessionBridge) {
        // Detach any prior bridge for this device to avoid leaking its
        // idleTimer / outputBufferTask / pending byte buffer when the same
        // deviceId reconnects (e.g. WebSocket reconnect before old close).
        if let existing = activeBridges[bridge.deviceId] {
            existing.detach()
        }
        activeBridges[bridge.deviceId] = bridge
        connectedDeviceCount = activeBridges.count

        // Connect raw PTY output → bridge.
        // Uses onRawData to relay unprocessed ANSI bytes (not parsed lines)
        // so the remote xterm.js can render colors, cursor movements, etc.
        if let view = terminalService.terminalView(for: bridge.sessionId) {
            view.onRawData = { [weak bridge] (_, slice) in
                guard let bridge else { return }
                // SwiftTerm fires onRawData from updateDisplay() which is dispatched on
                // DispatchQueue.main — i.e. we are already on the main thread/MainActor
                // executor. Spawning a Task per PTY chunk (potentially thousands/sec)
                // floods the executor. Hop via MainActor.assumeIsolated, matching the
                // pattern in TerminalService.installCallbacks (onRangeChanged).
                //
                // ArraySlice<UInt8> is not Sendable so we eagerly copy into Data which
                // IS Sendable. RemoteSessionBridge.handleRawData accepts Data.
                let data = Data(slice)
                MainActor.assumeIsolated {
                    bridge.handleRawData(data)
                }
            }
            #if DEBUG
            NSLog("[RC-BRIDGE] onRawData installed for session=\(bridge.sessionId) view=\(ObjectIdentifier(view))")
            #endif
        } else {
            Logger.remoteControl.warning(
                "registerBridge: no terminal view for session=\(bridge.sessionId)"
            )
        }

        Logger.remoteControl.info(
            "Bridge registered: device=\(bridge.deviceId) session=\(bridge.sessionId)"
        )
    }

    /// Unregister a session bridge (called on WebSocket close).
    func unregisterBridge(_ bridge: RemoteSessionBridge) {
        bridge.detach()

        // Remove from the registry first so the "any other bridge" scan below
        // does not see ourselves.
        activeBridges.removeValue(forKey: bridge.deviceId)

        // Only clear the terminal view's onRawData when no other bridge is
        // still attached to the same session — otherwise a concurrent bridge
        // (different deviceId, same sessionId) would lose its output stream.
        let stillAttached = activeBridges.values.contains { $0.sessionId == bridge.sessionId }
        if !stillAttached, let view = terminalService.terminalView(for: bridge.sessionId) {
            view.onRawData = nil
        }

        connectedDeviceCount = activeBridges.count
        Logger.remoteControl.info(
            "Bridge unregistered: device=\(bridge.deviceId) session=\(bridge.sessionId)"
        )
    }

    // MARK: - Broadcast

    /// Broadcast a JSON text message to all active bridges.
    func broadcastTextMessage(_ json: String) {
        for (_, bridge) in activeBridges {
            bridge.sendTextMessage(json)
        }
    }

    // MARK: - Private: Ngrok Host Observation

    /// Observe `NgrokTunnelService.tunnelURL` and push the resolved host into
    /// `ngrokHostRef` so all active ``HTTPRequestRouter`` instances receive it.
    ///
    /// This replaces the broad `*.ngrok-free.app` wildcard in `allowedOrigin`
    /// with an exact host match for the current tunnel, eliminating the attack
    /// surface of any other ngrok-free.app subdomain being accepted as a CORS origin.
    private func startNgrokHostObservation() {
        ngrokHostObservationTask = Task { @MainActor [weak self] in
            guard let self else { return }
            let ngrokSvc = self.ngrok
            while !Task.isCancelled {
                let holder = ContinuationHolder()
                await withTaskCancellationHandler {
                    await withCheckedContinuation { (c: CheckedContinuation<Void, Never>) in
                        holder.set(c)
                        withObservationTracking {
                            _ = ngrokSvc.tunnelURL
                        } onChange: {
                            holder.resume()
                        }
                    }
                } onCancel: {
                    holder.resume()
                }
                guard !Task.isCancelled else { return }

                let host: String?
                if let rawURL = ngrokSvc.tunnelURL,
                   let parsed = URL(string: rawURL),
                   let h = parsed.host {
                    host = h
                } else {
                    host = nil
                }
                self.ngrokHostRef.set(host)
                Logger.remoteControl.debug(
                    "RemoteControlServer: ngrok CORS host updated to \(host ?? "nil", privacy: .public)"
                )
            }
        }
    }

    // MARK: - Private: Static File Cache

    /// Pre-load all files from the `RemoteControlWeb` bundle directory into memory.
    ///
    /// Called once on `start()` (on MainActor, before the detached NIO bootstrap task)
    /// so that static file serving never blocks NIO event loop threads on disk I/O.
    ///
    /// Returns a dictionary keyed by relative path (e.g. `"index.html"`,
    /// `"vendor/xterm.js"`) mapping to `(data, contentType)`.
    private static func loadStaticFileCache() -> [String: (Data, String)] {
        guard let baseURL = Bundle.main.url(forResource: "RemoteControlWeb", withExtension: nil) else {
            Logger.remoteControl.warning("RemoteControlServer: RemoteControlWeb bundle not found — static files will be served from disk")
            return [:]
        }

        var cache: [String: (Data, String)] = [:]
        let fm = FileManager.default
        guard let enumerator = fm.enumerator(
            at: baseURL,
            includingPropertiesForKeys: [.isRegularFileKey],
            options: [.skipsHiddenFiles]
        ) else {
            return cache
        }

        for case let fileURL as URL in enumerator {
            guard (try? fileURL.resourceValues(forKeys: [.isRegularFileKey]))?.isRegularFile == true else {
                continue
            }
            // Compute the relative path inside the bundle directory.
            let fullPath = fileURL.standardized.path
            let basePath = baseURL.standardized.path
            guard fullPath.hasPrefix(basePath) else { continue }
            var relativePath = String(fullPath.dropFirst(basePath.count))
            if relativePath.hasPrefix("/") { relativePath = String(relativePath.dropFirst()) }

            guard let data = try? Data(contentsOf: fileURL) else { continue }
            let ext = (relativePath as NSString).pathExtension
            let contentType = mimeTypeStatic(for: ext)
            cache[relativePath] = (data, contentType)
        }

        Logger.remoteControl.info(
            "RemoteControlServer: pre-loaded \(cache.count) static files from RemoteControlWeb bundle"
        )
        return cache
    }

    private static func mimeTypeStatic(for ext: String) -> String {
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

    // MARK: - Private: Session Observation

    /// Observe `TerminalService.sessionsByProject` and broadcast
    /// `sessions_changed` messages to all connected WebSocket clients.
    private func startSessionObservation() {
        sessionObservationTask = Task { @MainActor [weak self] in
            guard let self else { return }
            let terminalService = self.terminalService
            while !Task.isCancelled {
                let holder = ContinuationHolder()
                await withTaskCancellationHandler {
                    await withCheckedContinuation { (c: CheckedContinuation<Void, Never>) in
                        holder.set(c)
                        withObservationTracking {
                            _ = terminalService.sessionsByProject
                        } onChange: {
                            holder.resume()
                        }
                    }
                } onCancel: {
                    holder.resume()
                }
                guard !Task.isCancelled else { return }

                // Sessions changed -- broadcast to all bridges.
                Logger.remoteControl.info("sessionsByProject changed -- broadcasting to \(self.activeBridges.count) bridge(s)")
                let encoder = JSONEncoder()
                for (projectId, sessions) in terminalService.sessionsByProject {
                    let sessionResponses = sessions.map { session in
                        SessionResponse(
                            id: session.id.uuidString,
                            title: session.title,
                            state: session.state.remoteAPIString,
                            isAgent: session.isAgentSession,
                            hasRemoteAttachment: self.activeBridges.values.contains {
                                $0.sessionId == session.id
                            },
                            attachedDeviceId: self.activeBridges.values.first {
                                $0.sessionId == session.id
                            }?.deviceId.uuidString
                        )
                    }
                    let msg = WSSessionsChangedMessage(
                        type: "sessions_changed",
                        projectId: projectId.uuidString,
                        sessions: sessionResponses
                    )
                    if let data = try? encoder.encode(msg),
                       let json = String(data: data, encoding: .utf8) {
                        self.broadcastTextMessage(json)
                    }
                }
            }
        }
    }
}

// MARK: - NgrokHostRef

/// Thread-safe container for the current ngrok tunnel host.
///
/// Updated on MainActor when the ngrok URL is resolved; read on NIO event loop
/// threads inside ``HTTPRequestRouter.allowedOrigin(from:)``.
///
/// Uses `NSLock` rather than an actor to avoid async overhead in the hot-path
/// CORS check (one read per HTTP request).
final class NgrokHostRef: @unchecked Sendable {
    private let lock = NSLock()
    private var _host: String?

    /// The current ngrok hostname (e.g. `"xxxx.ngrok-free.app"`), or `nil`
    /// when no tunnel is active.
    var host: String? {
        lock.lock()
        defer { lock.unlock() }
        return _host
    }

    func set(_ newHost: String?) {
        lock.lock()
        _host = newHost
        lock.unlock()
    }
}

// MARK: - TerminalSessionState + Remote API

extension TerminalSessionState {
    /// Convert session state to the REST/WS API string representation.
    var remoteAPIString: String {
        switch self {
        case .running: return "running"
        case .hasActivity: return "has_activity"
        case .exited: return "exited"
        }
    }
}

// MARK: - Preview Stub

/// Minimal `ProjectManaging` stub for the preview/default convenience init.
/// Not `@Observable` -- only used to satisfy the protocol requirement in
/// SwiftUI environment key defaults where the server is never started.
@Observable
@MainActor
private final class PreviewProjectManagerStub: ProjectManaging {
    var projects: [Project] = []
    var activeProjectId: UUID?
    var recentHistory: [Project] = []
    var recentProjects: [Project] = []
    func addProject(at path: URL) throws -> Project {
        throw ProjectManagerError.invalidPath(path)
    }
    func removeProject(_ id: UUID) throws {}
    func updateProject(_ id: UUID, _ mutate: (inout Project) -> Void) throws {}
    func moveProjects(from indices: IndexSet, to destination: Int) {}
    func project(for id: UUID) -> Project? { nil }
    func project(at path: URL) -> Project? { nil }
    func load() throws {}
    func save() throws {}
}

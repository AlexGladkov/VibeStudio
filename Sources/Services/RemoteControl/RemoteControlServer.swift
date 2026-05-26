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

    private(set) var authService: RemoteAuthService
    private(set) var preferences: RemoteControlPreferences
    private(set) var terminalService: TerminalService
    private(set) var projectManager: any ProjectManaging
    private let bonjour: BonjourAdvertiser
    private let ngrok: NgrokTunnelService

    // MARK: - NIO State

    /// `nonisolated(unsafe)` because `MultiThreadedEventLoopGroup` is Sendable
    /// but not `@MainActor`.
    nonisolated(unsafe) private var eventLoopGroup: MultiThreadedEventLoopGroup?

    /// The bound server channel (HTTPS).
    nonisolated(unsafe) private var serverChannel: Channel?

    /// Plain HTTP channel for LAN access (port + 1). Avoids iOS Safari
    /// self-signed cert issues with WebSocket.
    nonisolated(unsafe) private var httpChannel: Channel?

    // MARK: - Active Bridges

    /// Active session bridges keyed by device ID.
    private(set) var activeBridges: [UUID: RemoteSessionBridge] = [:]

    // MARK: - Server Metadata

    /// Timestamp when the server was started (for uptime calculation).
    private var startedAt: Date?

    /// Observation task for session changes broadcast.
    nonisolated(unsafe) private var sessionObservationTask: Task<Void, Never>?

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
        projectManager: any ProjectManaging
    ) {
        self.authService = authService
        self.preferences = preferences
        self.terminalService = terminalService
        self.projectManager = projectManager
        self.bonjour = BonjourAdvertiser()
        self.ngrok = NgrokTunnelService()
    }

    /// Convenience init for previews and SwiftUI environment key defaults.
    ///
    /// Creates a server with fresh default services that are never started.
    /// The `terminalService` and `projectManager` are stubs -- this server
    /// instance is only used for preview layout, never for networking.
    convenience init() {
        let prefs = RemoteControlPreferences()
        let theme = ThemeService()
        let general = GeneralPreferences()
        let terminal = TerminalService(themeService: theme, generalPreferences: general)
        self.init(
            authService: RemoteAuthService(),
            preferences: prefs,
            terminalService: terminal,
            projectManager: PreviewProjectManagerStub()
        )
    }

    deinit {
        sessionObservationTask?.cancel()
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

        isTransitioning = true
        weak var weakSelf = self

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
                        serverRef: weakSelf
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
                    server.authService.onSecurityLockout = { [weak server] in
                        server?.handleSecurityLockout()
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
                await MainActor.run {
                    weakSelf?.isTransitioning = false
                }
            }
        }
    }

    /// Gracefully stop the server and disconnect all devices.
    func stop() {
        guard isRunning, !isTransitioning else { return }

        isTransitioning = true

        // Detach all bridges.
        for (_, bridge) in activeBridges {
            bridge.detach()
        }
        activeBridges.removeAll()
        connectedDeviceCount = 0

        bonjour.unpublish()
        ngrok.stop()
        authService.revokeAllDevices()

        sessionObservationTask?.cancel()
        sessionObservationTask = nil

        let channel = serverChannel
        let httpCh = httpChannel
        let group = eventLoopGroup

        isRunning = false
        startedAt = nil
        serverChannel = nil
        httpChannel = nil
        eventLoopGroup = nil

        weak var weakSelf = self

        Task.detached {
            if let channel {
                try? await channel.close().get()
            }
            if let httpCh {
                try? await httpCh.close().get()
            }
            if let group {
                try? await group.shutdownGracefully()
            }
            Logger.remoteControl.info("RemoteControlServer stopped")
            await MainActor.run {
                weakSelf?.isTransitioning = false
            }
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
        activeBridges[bridge.deviceId] = bridge
        connectedDeviceCount = activeBridges.count

        // Connect raw PTY output → bridge.
        // Uses onRawData to relay unprocessed ANSI bytes (not parsed lines)
        // so the remote xterm.js can render colors, cursor movements, etc.
        if let view = terminalService.terminalView(for: bridge.sessionId) {
            view.onRawData = { [weak bridge] (_, slice) in
                guard let bridge else { return }
                // Explicit MainActor dispatch — dataReceived fires on PTY read thread,
                // but handleRawData is @MainActor.
                Task { @MainActor in
                    bridge.handleRawData(slice)
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

        // Disconnect terminal output callback.
        if let view = terminalService.terminalView(for: bridge.sessionId) {
            view.onRawData = nil
        }

        activeBridges.removeValue(forKey: bridge.deviceId)
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

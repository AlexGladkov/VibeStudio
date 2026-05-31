// MARK: - RemoteControlServer
// Main @Observable facade for the embedded HTTP/WebSocket server.
// After Wave-4 (ARCH-H7) lifecycle stays here; static-file cache,
// bridge registry and Ngrok host ref live in their own files.
//
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
/// **Threading model:**
/// - All public API is `@MainActor` (SwiftUI-safe).
/// - NIO runs on its own `MultiThreadedEventLoopGroup` threads.
/// - `start()` / `stop()` use `Task.detached` for NIO bootstrap, then
///   hop back to MainActor for state updates.
@Observable
@MainActor
final class RemoteControlServer {

    // MARK: - Observable State

    /// Whether the NIO server is currently listening.
    private(set) var isRunning: Bool = false

    /// Guards against overlapping start/stop transitions.
    private var isTransitioning: Bool = false

    /// Number of devices with active WebSocket connections.
    ///
    /// ARCH-M9: computed from ``activeBridges`` so the value cannot diverge
    /// from the real bridge count. SwiftUI re-renders trigger via
    /// `@Observable` tracking on `activeBridges`.
    var connectedDeviceCount: Int { activeBridges.count }

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
    /// General app preferences forwarded to every ``HTTPRequestRouter`` instance.
    private(set) var generalPreferences: GeneralPreferences
    private let bonjour: BonjourAdvertiser
    private let ngrok: NgrokTunnelService

    // MARK: - Components (ARCH-H7)

    /// Bridge registry — owns `activeBridges`, register/unregister/broadcast.
    private let bridgeRegistry: RemoteBridgeRegistry

    /// Pre-loaded static file cache + baseURL captured on MainActor.
    /// Created on `start()` so unit tests / previews never pay the cost.
    private var staticCache: RemoteStaticFileCache = .empty

    /// Bundle metadata snapshot (app version) captured on MainActor.
    private let metadata: RemoteServerMetadata

    /// Pass-through accessor used by the HTTP router for BOLA enforcement
    /// and session listings. Reads observable state via `bridgeRegistry`.
    var activeBridges: [UUID: RemoteSessionBridge] {
        bridgeRegistry.activeBridges
    }

    // MARK: - NIO State

    /// All NIO state mutated only on MainActor.
    private var eventLoopGroup: MultiThreadedEventLoopGroup?

    /// The bound server channel (HTTPS).
    private var serverChannel: Channel?

    /// Plain HTTP channel for LAN access (port + 1). Avoids iOS Safari
    /// self-signed cert issues with WebSocket.
    private var httpChannel: Channel?

    // MARK: - Server Metadata

    /// Timestamp when the server was started (for uptime calculation).
    private var startedAt: Date?

    /// Observation task for session changes broadcast.
    nonisolated(unsafe) private var sessionObservationTask: Task<Void, Never>?

    /// Observation task that keeps `ngrokHostRef` in sync with the tunnel URL.
    nonisolated(unsafe) private var ngrokHostObservationTask: Task<Void, Never>?

    /// Shared ngrok host reference passed to all HTTPRequestRouter instances.
    private let ngrokHostRef = NgrokHostRef()

    /// Uptime in seconds since server start.
    var uptimeSeconds: Int {
        guard let startedAt else { return 0 }
        return Int(Date().timeIntervalSince(startedAt))
    }

    // MARK: - Init

    init(
        authService: RemoteAuthService,
        preferences: RemoteControlPreferences,
        generalPreferences: GeneralPreferences,
        terminalService: TerminalService,
        projectManager: any ProjectManaging
    ) {
        self.authService = authService
        self.preferences = preferences
        self.generalPreferences = generalPreferences
        self.terminalService = terminalService
        self.projectManager = projectManager
        self.bonjour = BonjourAdvertiser()
        self.ngrok = NgrokTunnelService()
        self.bridgeRegistry = RemoteBridgeRegistry(terminalService: terminalService)
        self.metadata = RemoteServerMetadata.current()
    }

    /// Convenience init for previews and SwiftUI environment key defaults.
    convenience init() {
        let prefs = RemoteControlPreferences()
        let theme = ThemeService()
        let general = GeneralPreferences()
        let terminal = TerminalService(themeService: theme, generalPreferences: general)
        self.init(
            authService: RemoteAuthService(),
            preferences: prefs,
            generalPreferences: general,
            terminalService: terminal,
            projectManager: PreviewProjectManagerStub()
        )
    }

    deinit {
        sessionObservationTask?.cancel()
        ngrokHostObservationTask?.cancel()
    }

    // MARK: - Server Lifecycle

    /// Start the HTTPS/WSS server.
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
        let generalPrefs = self.generalPreferences
        let idleTimeout = self.preferences.idleTimeoutMinutes

        isTransitioning = true
        weak var weakSelf = self

        // ARCH-H8: pre-load static files + baseURL on MainActor so NIO
        // threads never call Bundle.main themselves.
        let staticCache = RemoteStaticFileCache.load()
        self.staticCache = staticCache

        // Capture immutable metadata + ngrok host ref for the routers.
        let ngrokRef = ngrokHostRef
        let appMetadata = metadata

        Task.detached {
            do {
                // Ensure TLS certificate exists.
                let (certPath, keyPath) = try TLSCertificateManager.ensureCertificate()

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
                let configureChildChannel: @Sendable (Channel) -> EventLoopFuture<Void> = { channel in
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
                        generalPreferences: generalPrefs,
                        idleTimeoutMinutes: idleTimeout,
                        serverRef: weakSelf,
                        staticCache: staticCache,
                        ngrokHostRef: ngrokRef,
                        metadata: appMetadata
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
                        ChannelOptions.socketOption(.so_reuseaddr), value: 1
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
                        ChannelOptions.socketOption(.so_reuseaddr), value: 1
                    )
                    .childChannelInitializer(configureChildChannel)

                // SECURITY: plain HTTP always bound to loopback only.
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
                    // ARCH-M9: `connectedDeviceCount` is now computed from
                    // `activeBridges` — no longer set from
                    // `RemoteAuthService.onDevicesChanged`. We still observe
                    // device-list changes via the @Observable model, which
                    // updates the views directly.

                    if server.preferences.bonjourEnabled && !server.preferences.bindToLocalhost {
                        server.bonjour.publish(port: bindPort)
                    }

                    if server.preferences.ngrokEnabled && !server.preferences.ngrokAuthtoken.isEmpty {
                        // SECURITY (H1): tunnel the plain-HTTP loopback listener.
                        server.ngrok.start(
                            httpPort: bindPort + 1,
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

    /// Gracefully stop the server and disconnect all devices (fire-and-forget).
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
    func stopAsync() async {
        guard isRunning, !isTransitioning else { return }

        isTransitioning = true
        let (channel, httpCh, group) = beginStopCleanup()

        await Self.shutdownNIO(channel: channel, httpChannel: httpCh, group: group)
        Logger.remoteControl.info("RemoteControlServer stopped (async)")
        isTransitioning = false
    }

    @discardableResult
    private func beginStopCleanup() -> (Channel?, Channel?, MultiThreadedEventLoopGroup?) {
        // ARCH-H7: bridge teardown delegated to the registry.
        bridgeRegistry.detachAll()

        bonjour.unpublish()
        ngrok.stop()
        ngrokHostRef.set(nil)
        authService.revokeAllDevices()

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
        // SECURITY (H1): tunnel `port + 1` (plain-HTTP loopback).
        ngrok.start(
            httpPort: preferences.remoteControlPort + 1,
            authtoken: preferences.ngrokAuthtoken
        )
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
    func disconnect(_ deviceId: UUID) {
        bridgeRegistry.removeBridge(forDevice: deviceId)
        authService.revokeDevice(deviceId)
        RemoteAuditLog.deviceDisconnect(deviceId: deviceId, reason: "kicked_by_host")
    }

    // MARK: - Bridge Registration (delegates to RemoteBridgeRegistry)

    /// Register a new session bridge (called from NIO handlers via MainActor hop).
    func registerBridge(_ bridge: RemoteSessionBridge) {
        bridgeRegistry.registerBridge(bridge)
    }

    /// Unregister a session bridge (called on WebSocket close).
    func unregisterBridge(_ bridge: RemoteSessionBridge) {
        bridgeRegistry.unregisterBridge(bridge)
    }

    // MARK: - Broadcast

    /// Broadcast a JSON text message to all active bridges.
    func broadcastTextMessage(_ json: String) {
        bridgeRegistry.broadcastTextMessage(json)
    }

    // MARK: - Private: Ngrok Host Observation

    /// Observe `NgrokTunnelService.tunnelURL` and push the resolved host into
    /// `ngrokHostRef` so all active ``HTTPRequestRouter`` instances receive it.
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

                // Sessions changed — broadcast to all bridges.
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

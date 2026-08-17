// MARK: - RemoteControlServer
// Main @Observable facade for the embedded HTTP/WebSocket server.
// After Wave-4 (ARCH-H7) lifecycle stays here; static-file cache,
// bridge registry and Ngrok host ref live in their own files.
//
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOPosix
import Observation
import OSLog
import UserNotifications

// WebSocket upgrade is handled manually in HTTPRequestRouter (not via NIO's
// HTTPServerUpgradeHandler, which is one-shot and breaks on HTTP/1.1 keep-alive).
// NIO bootstrap/TLS lives in RemoteControlServer+Bootstrap.

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
    /// `internal` so ``RemoteControlServer+Lifecycle`` can flip it.
    var isTransitioning: Bool = false

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
    /// Optional — populated after `AppDelegate` wires it post-init.
    weak var costTrackerService: CostTrackerService?
    private let bonjour: BonjourAdvertiser
    /// Ngrok tunnel service. `internal` so ``RemoteControlServer+Observation``
    /// can observe its `tunnelURL`.
    let ngrok: NgrokTunnelService

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
    /// `internal` so ``RemoteControlServer+Observation`` can own it.
    nonisolated(unsafe) var sessionObservationTask: Task<Void, Never>?

    /// Observation task that keeps `ngrokHostRef` in sync with the tunnel URL.
    /// `internal` so ``RemoteControlServer+Observation`` can own it.
    nonisolated(unsafe) var ngrokHostObservationTask: Task<Void, Never>?

    /// Observation task for `cost_update` WS broadcasts.
    /// `internal` so ``RemoteControlServer+Observation`` can own it.
    nonisolated(unsafe) var _costObservationTask: Task<Void, Never>?

    /// Shared ngrok host reference passed to all HTTPRequestRouter instances.
    /// `internal` so ``RemoteControlServer+Observation`` can update it.
    let ngrokHostRef = NgrokHostRef()

    /// Reused encoder for `sessions_changed` broadcasts. Hoisted out of the
    /// observation loop so it is not re-allocated on every wakeup (hot path).
    /// `internal` so ``RemoteControlServer+Observation`` can use it.
    let sessionsEncoder = JSONEncoder()

    /// Uptime in seconds since server start.
    var uptimeSeconds: Int {
        guard let startedAt else { return 0 }
        return Int(Date().timeIntervalSince(startedAt))
    }

    // MARK: - Init

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
            terminalService: terminal,
            projectManager: PreviewProjectManagerStub()
        )
    }

    deinit {
        sessionObservationTask?.cancel()
        ngrokHostObservationTask?.cancel()
        _costObservationTask?.cancel()
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

        isTransitioning = true

        // ARCH-H8: pre-load static files + baseURL on MainActor so NIO
        // threads never call Bundle.main themselves.
        let staticCache = RemoteStaticFileCache.load()
        self.staticCache = staticCache

        var config = RouterRuntimeConfig(
            authService: authService, terminalService: terminalService,
            projectManager: projectManager, preferences: preferences,
            idleTimeoutMinutes: preferences.idleTimeoutMinutes,
            staticCache: staticCache, ngrokHostRef: ngrokHostRef, metadata: metadata
        )
        config.costTrackerService = costTrackerService
        // `weakSelf` is re-weakened inside the configurator (see
        // ``makeChildChannelConfigurator``) so the router's `serverRef` stays
        // weak and no retain cycle forms through the bootstrap-held initializer.
        weak var weakSelf = self
        let configureChildChannel = Self.makeChildChannelConfigurator(config: config, serverRef: weakSelf)

        Task.detached {
            do {
                let bound = try await Self.bindChannels(
                    configureChildChannel: configureChildChannel,
                    bindHost: bindHost, bindPort: bindPort
                )
                await MainActor.run {
                    weakSelf?.finishStartup(
                        group: bound.group, channel: bound.channel,
                        httpChannel: bound.httpChannel, bindHost: bindHost, bindPort: bindPort
                    )
                }
            } catch {
                Logger.remoteControl.error(
                    "RemoteControlServer failed to start: \(error.localizedDescription, privacy: .public)"
                )
                await MainActor.run { weakSelf?.isTransitioning = false }
            }
        }
    }

    // MARK: - Private: Startup Commit

    /// Commit bound NIO state on the MainActor and start the ancillary
    /// services (session observation, ngrok host tracking, Bonjour, ngrok).
    private func finishStartup(
        group: MultiThreadedEventLoopGroup,
        channel: Channel,
        httpChannel httpCh: Channel?,
        bindHost: String,
        bindPort: Int
    ) {
        eventLoopGroup = group
        serverChannel = channel
        httpChannel = httpCh
        isRunning = true
        isTransitioning = false
        startedAt = Date()
        startSessionObservation()
        startNgrokHostObservation()
        startCostObservation()
        authService.onSecurityLockout = { [weak self] in
            self?.handleSecurityLockout()
        }
        // ARCH-M9: `connectedDeviceCount` is now computed from
        // `activeBridges` — no longer set from
        // `RemoteAuthService.onDevicesChanged`. We still observe
        // device-list changes via the @Observable model, which
        // updates the views directly.

        if preferences.bonjourEnabled && !preferences.bindToLocalhost {
            bonjour.publish(port: bindPort)
        }

        if preferences.ngrokEnabled && !preferences.ngrokAuthtoken.isEmpty {
            // SECURITY (H1): tunnel the plain-HTTP loopback listener.
            ngrok.start(
                httpPort: bindPort + 1,
                authtoken: preferences.ngrokAuthtoken
            )
        }

        Logger.remoteControl.info(
            "RemoteControlServer started on \(bindHost, privacy: .public):\(bindPort)"
        )
    }

    // MARK: - Private: Teardown Snapshot

    /// Detach the server's observable + NIO state on the MainActor and hand the
    /// raw NIO handles to ``RemoteControlServer+Lifecycle`` for off-actor
    /// shutdown. `internal` so the lifecycle extension can drive it.
    @discardableResult
    func beginStopCleanup() -> NIOTeardownHandles {
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

        return NIOTeardownHandles(serverChannel: channel, httpChannel: httpCh, group: group)
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
        content.title = String(localized: "Remote Control disabled")
        content.body = String(localized: "10 failed login attempts detected. The server has been stopped for security.")
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

    /// Broadcast to bridges for a specific session only (BOLA protection).
    func broadcastToSession(_ sessionId: UUID, json: String) {
        bridgeRegistry.broadcastToSession(sessionId, json: json)
    }

    // Long-lived observation loops live in ``RemoteControlServer+Observation``.
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

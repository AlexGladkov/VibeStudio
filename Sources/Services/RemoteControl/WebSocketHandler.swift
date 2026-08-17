// MARK: - RemoteWebSocketHandler
// NIO WebSocket handler for bidirectional terminal I/O.
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOWebSocket
import OSLog
import os

/// NIO channel handler for WebSocket connections to terminal sessions.
///
/// After the HTTP-to-WebSocket upgrade completes, this handler manages
/// the full lifecycle of a remote terminal session:
///
/// 1. **Channel active:** Wait for authentication (first text message).
/// 2. **First text frame:** Must be `{"type":"auth","token":"..."}`. Validates
///    via ``RemoteAuthService``. On success, creates ``RemoteSessionBridge``.
/// 3. **Subsequent text frames:** Parse JSON, dispatch to bridge (input/resize/ping/detach).
/// 4. **Binary frames:** Not expected from clients -- ignored.
/// 5. **Close frame:** Clean up bridge, unregister from server.
/// 6. **Heartbeat:** If no message for 60s, close with `WSCloseCode.heartbeatTimeout`.
///
/// **Security:**
/// Token is sent as the first WS message (not in URL query params) to prevent
/// leakage in server logs, browser history, and Referer headers.
///
/// **Threading model:**
/// This handler runs on a NIO `EventLoop` thread. All bridge operations
/// (which are `@MainActor`) are dispatched via `Task { @MainActor in }`.
/// The `Channel` reference (Sendable) is cached; `ChannelHandlerContext`
/// is NEVER captured across isolation boundaries.
final class RemoteWebSocketHandler: ChannelInboundHandler, @unchecked Sendable {

    typealias InboundIn = WebSocketFrame
    typealias OutboundOut = WebSocketFrame

    // MARK: - State

    /// The session bridge (created after authentication, MainActor-owned).
    /// Protected by `NSLock` to allow safe reads from the NIO event loop
    /// thread while writes occur from MainActor context.
    private let bridgeLock = NSLock()
    private var _bridge: RemoteSessionBridge?

    /// Cached channel reference (Sendable, unlike ChannelHandlerContext).
    var cachedChannel: Channel?

    /// The terminal session ID extracted from the URL path.
    private let sessionId: UUID

    /// Device info — nil until first message authenticates successfully.
    var deviceInfo: RemoteDevice?

    /// Whether the connection has been authenticated via first WS message.
    var isAuthenticated = false

    /// Guards against a data race where two rapid frames both pass `!isAuthenticated`
    /// before the MainActor auth Task has a chance to set `isAuthenticated = true`.
    /// Set synchronously on the NIO event loop thread before dispatching to MainActor.
    var authInProgress = false

    /// Auth service for validating the token on first message.
    let authService: RemoteAuthService?

    /// Client IP for token validation.
    let clientIP: String

    /// Weak reference to the server for bridge registration.
    private weak var serverRef: RemoteControlServer?

    /// Terminal service for I/O.
    private let terminalService: TerminalService

    /// Idle timeout minutes from preferences.
    private let idleTimeoutMinutes: Int

    /// Bundled lifecycle Tasks (heartbeat + auth-timeout). Held under an
    /// `OSAllocatedUnfairLock` so that every access from the NIO event loop,
    /// the MainActor auth success path, and `channelInactive` cleanup uses
    /// the same serialisation primitive instead of the previous
    /// `nonisolated(unsafe)` pattern (which silently relied on the caller
    /// always being on the event loop — false in success-path cancellation).
    ///
    /// `Task<Void, Never>` is intentionally non-Sendable in Swift 5.10,
    /// hence the `@unchecked Sendable` wrapper. The lock makes the access
    /// safe regardless.
    private struct LifecycleTasks: @unchecked Sendable {
        var heartbeat: Task<Void, Never>?
        var authTimeout: Task<Void, Never>?
    }

    private let lifecycleLock = OSAllocatedUnfairLock(initialState: LifecycleTasks())

    /// JSON decoder.
    let decoder = JSONDecoder()

    /// JSON encoder (reused across pong responses).
    let pongEncoder = JSONEncoder()

    // MARK: - Init

    /// Create a WebSocket handler for a terminal session.
    ///
    /// - Parameters:
    ///   - sessionId: The terminal session to attach to.
    ///   - deviceInfo: Pre-authenticated device (nil = auth via first message).
    ///   - serverRef: The server for bridge registration/unregistration.
    ///   - terminalService: Terminal service for PTY I/O.
    ///   - idleTimeoutMinutes: Idle timeout from preferences.
    ///   - authService: Auth service for first-message validation.
    ///   - clientIP: Client IP for token validation.
    init(
        sessionId: UUID,
        deviceInfo: RemoteDevice?,
        serverRef: RemoteControlServer?,
        terminalService: TerminalService,
        idleTimeoutMinutes: Int,
        authService: RemoteAuthService? = nil,
        clientIP: String = ""
    ) {
        self.sessionId = sessionId
        self.deviceInfo = deviceInfo
        self.isAuthenticated = deviceInfo != nil
        self.serverRef = serverRef
        self.terminalService = terminalService
        self.idleTimeoutMinutes = idleTimeoutMinutes
        self.authService = authService
        self.clientIP = clientIP
    }

    // MARK: - Channel Lifecycle

    func handlerAdded(context: ChannelHandlerContext) {
        #if DEBUG
        HTTPRequestRouter.wsLog("[WSH] handlerAdded isActive=\(context.channel.isActive)")
        #endif
        if context.channel.isActive {
            onChannelReady(channel: context.channel)
        }
    }

    func channelActive(context: ChannelHandlerContext) {
        #if DEBUG
        HTTPRequestRouter.wsLog("[WSH] channelActive fired")
        #endif
        if cachedChannel == nil {
            onChannelReady(channel: context.channel)
        }
    }

    /// Called when channel is ready. If pre-authenticated, initialize session
    /// immediately. Otherwise, start auth timeout.
    private func onChannelReady(channel: Channel) {
        cachedChannel = channel

        if isAuthenticated, let deviceInfo {
            initializeSession(channel: channel, device: deviceInfo)
        } else {
            // Start auth timeout — client must send auth within 10 seconds.
            startAuthTimeout(channel: channel)
        }

        resetHeartbeat(channel: channel)
    }

    func initializeSession(channel: Channel, device: RemoteDevice) {
        let sessionId = self.sessionId
        let termSvc = self.terminalService
        let idleTimeout = self.idleTimeoutMinutes
        let serverRef = self.serverRef

        #if DEBUG
        HTTPRequestRouter.wsLog("[WSH] initializeSession: session=\(sessionId) device=\(device.id)")
        #endif

        Task { @MainActor [weak self] in
            let bridge = RemoteSessionBridge(
                deviceId: device.id,
                sessionId: sessionId,
                channel: channel,
                terminalService: termSvc,
                idleTimeoutMinutes: idleTimeout
            )
            self?.setBridge(bridge)
            // Register BEFORE starting streaming so the BOLA guard in
            // RemoteBridgeRegistry runs first. If the sessionId does not exist,
            // `registerBridge` calls `bridge.detach()` + `bridge.closeTransport()`
            // and returns without adding the bridge to `activeBridges`.
            // Calling `startStreaming()` before `registerBridge()` would arm the
            // idle timer for sessions that are about to be rejected — leaving a
            // zombie socket open for up to `idleTimeoutMinutes` instead of closing
            // the channel immediately on rejection.
            serverRef?.registerBridge(bridge)
            bridge.startStreaming()
            RemoteAuditLog.deviceConnect(device: device, sessionId: sessionId)
        }
    }

    func channelInactive(context: ChannelHandlerContext) {
        #if DEBUG
        HTTPRequestRouter.wsLog("[WSH] channelInactive")
        #endif
        cancelHeartbeat()
        cancelAuthTimeout()

        let bridge = takeBridge()
        let serverRef = self.serverRef
        let deviceId = deviceInfo?.id

        // P0-2 fix: channelInactive previously called only `unregisterBridge`,
        // leaving `authService.connectedDevices` with a stale zombie entry for
        // the disconnected device. After 3 such cycles `validatePin` returned
        // `maxDevicesReached` for 4 h (the token TTL). Now we mirror the canonical
        // teardown already used by `RemoteControlServer.disconnect(_:)`: atomically
        // clean BOTH registries in the same MainActor hop so the counts never diverge.
        Task { @MainActor in
            if let bridge {
                serverRef?.unregisterBridge(bridge)
            }
            if let deviceId {
                serverRef?.authService.revokeDevice(deviceId)
                RemoteAuditLog.deviceDisconnect(deviceId: deviceId, reason: "connection_closed")
            }
        }
        cachedChannel = nil
    }

    // Inbound frame handling + message dispatch live in
    // ``RemoteWebSocketHandler+Frames`` (Iteration 9 split).

    func errorCaught(context: ChannelHandlerContext, error: Error) {
        #if DEBUG
        HTTPRequestRouter.wsLog("[WSH] errorCaught: \(error.localizedDescription)")
        #endif
        Logger.remoteControl.error(
            "RemoteWebSocketHandler error: \(error.localizedDescription, privacy: .public)"
        )
        context.close(promise: nil)
    }

    // MARK: - Private: Auth Timeout

    /// Close connection if no auth message received within 10 seconds.
    private func startAuthTimeout(channel: Channel) {
        let task = Task { [weak self] in
            try? await Task.sleep(for: .seconds(RemoteWSTimeouts.authTimeoutSeconds))
            guard !Task.isCancelled, let self, !self.isAuthenticated else { return }
            Logger.remoteControl.warning("RemoteWebSocketHandler: auth timeout, closing unauthenticated connection")
            self.sendErrorAndClose(code: WSCloseCode.authRequired, reason: "Auth timeout", channel: channel)
        }
        // Replace any existing auth-timeout under the lock — and cancel the
        // displaced task so it can't fire after we've overwritten the slot.
        lifecycleLock.withLock { state in
            state.authTimeout?.cancel()
            state.authTimeout = task
        }
    }

    /// Cancel and clear the in-flight auth-timeout task, if any.
    func cancelAuthTimeout() {
        let prior: Task<Void, Never>? = lifecycleLock.withLock { state in
            let pending = state.authTimeout
            state.authTimeout = nil
            return pending
        }
        prior?.cancel()
    }

    /// Cancel and clear the in-flight heartbeat task, if any.
    private func cancelHeartbeat() {
        let prior: Task<Void, Never>? = lifecycleLock.withLock { state in
            let pending = state.heartbeat
            state.heartbeat = nil
            return pending
        }
        prior?.cancel()
    }

    // MARK: - Private: Close Handling

    func handleClose(context: ChannelHandlerContext, frame: WebSocketFrame) {
        let closeData = frame.unmaskedData
        let closeFrame = WebSocketFrame(fin: true, opcode: .connectionClose, data: closeData)
        context.writeAndFlush(wrapOutboundOut(closeFrame)).whenComplete { _ in
            context.close(promise: nil)
        }
    }

    // MARK: - Private: Bridge Access

    private func setBridge(_ bridge: RemoteSessionBridge) {
        bridgeLock.lock()
        _bridge = bridge
        bridgeLock.unlock()
    }

    func getBridge() -> RemoteSessionBridge? {
        bridgeLock.lock()
        defer { bridgeLock.unlock() }
        return _bridge
    }

    /// Atomically read and nil-out the bridge.
    private func takeBridge() -> RemoteSessionBridge? {
        bridgeLock.lock()
        let b = _bridge
        _bridge = nil
        bridgeLock.unlock()
        return b
    }

    // MARK: - Private: Heartbeat

    func resetHeartbeat(channel: Channel) {
        let task = Task { [weak self] in
            try? await Task.sleep(for: .seconds(RemoteWSTimeouts.heartbeatIntervalSeconds))
            guard !Task.isCancelled, self != nil else { return }
            Logger.remoteControl.info("RemoteWebSocketHandler: heartbeat timeout, closing connection")
            var buffer = channel.allocator.buffer(capacity: 2 + "Heartbeat timeout".utf8.count)
            buffer.writeInteger(WSCloseCode.heartbeatTimeout)
            buffer.writeString("Heartbeat timeout")
            let closeFrame = WebSocketFrame(fin: true, opcode: .connectionClose, data: buffer)
            channel.eventLoop.execute {
                channel.writeAndFlush(closeFrame).whenComplete { _ in
                    channel.close(promise: nil)
                }
            }
        }
        // Atomic swap under lock — cancel any prior heartbeat task so a
        // stale timer can't fire after we've reset.
        let prior: Task<Void, Never>? = lifecycleLock.withLock { state in
            let pending = state.heartbeat
            state.heartbeat = task
            return pending
        }
        prior?.cancel()
    }
}

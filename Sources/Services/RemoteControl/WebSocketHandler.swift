// MARK: - RemoteWebSocketHandler
// NIO WebSocket handler for bidirectional terminal I/O.
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOWebSocket
import OSLog

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
/// 6. **Heartbeat:** If no message for 120s, close with code 4004.
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
final class RemoteWebSocketHandler: ChannelInboundHandler {

    typealias InboundIn = WebSocketFrame
    typealias OutboundOut = WebSocketFrame

    // MARK: - State

    /// The session bridge (created after authentication, MainActor-owned).
    /// Protected by `NSLock` to allow safe reads from the NIO event loop
    /// thread while writes occur from MainActor context.
    private let bridgeLock = NSLock()
    private var _bridge: RemoteSessionBridge?

    /// Cached channel reference (Sendable, unlike ChannelHandlerContext).
    private var cachedChannel: Channel?

    /// The terminal session ID extracted from the URL path.
    private let sessionId: UUID

    /// Device info — nil until first message authenticates successfully.
    private var deviceInfo: RemoteDevice?

    /// Whether the connection has been authenticated via first WS message.
    private var isAuthenticated = false

    /// Guards against a data race where two rapid frames both pass `!isAuthenticated`
    /// before the MainActor auth Task has a chance to set `isAuthenticated = true`.
    /// Set synchronously on the NIO event loop thread before dispatching to MainActor.
    private var authInProgress = false

    /// Non-auth frames received while authentication is still in progress.
    ///
    /// The web client (`app.js`) sends `{"type":"auth"}` immediately followed by
    /// `{"type":"resize"}` in its WebSocket `onopen` handler, without waiting for
    /// the `auth_ok` reply. Because token validation hops to the MainActor, the
    /// `resize` frame can arrive on the event loop while `authInProgress == true`
    /// but `isAuthenticated == false`. Closing the connection in that window
    /// (code 4000) left the terminal stuck on "Loading session..." with a
    /// reconnect loop. Instead we buffer such frames here and replay them once
    /// the session bridge exists. Event-loop-thread only, like `isAuthenticated`.
    private var pendingPreAuthFrames: [Data] = []

    /// Upper bound on buffered pre-auth frames — bounds memory for a peer that
    /// has not yet authenticated. The legitimate client buffers at most one
    /// (`resize`); anything beyond a small cap is dropped.
    private static let maxPendingPreAuthFrames = 16

    /// Auth service for validating the token on first message.
    private let authService: RemoteAuthService?

    /// Client IP for token validation.
    private let clientIP: String

    /// Weak reference to the server for bridge registration.
    private weak var serverRef: RemoteControlServer?

    /// Terminal service for I/O.
    private let terminalService: TerminalService

    /// Idle timeout minutes from preferences.
    private let idleTimeoutMinutes: Int

    /// Heartbeat timer: close connection if no message received within 120 seconds.
    /// All reads and writes go through `bridgeLock` — never access directly.
    nonisolated(unsafe) private var _heartbeatTask: Task<Void, Never>?

    /// Auth timeout: close if no auth message within 10 seconds.
    /// All reads and writes go through `bridgeLock` — never access directly.
    nonisolated(unsafe) private var _authTimeoutTask: Task<Void, Never>?

    /// JSON decoder.
    private let decoder = JSONDecoder()

    /// JSON encoder (reused across pong responses).
    private let pongEncoder = JSONEncoder()

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

    private func initializeSession(channel: Channel, device: RemoteDevice) {
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
            bridge.startStreaming()
            serverRef?.registerBridge(bridge)
            RemoteAuditLog.deviceConnect(device: device, sessionId: sessionId)

            // Bridge is now live — replay any frames that raced ahead of auth
            // (e.g. the client's initial `resize`). Hop back to the event loop
            // so the buffer is touched only from its owning thread.
            channel.eventLoop.execute { self?.flushPendingPreAuthFrames(channel: channel) }
        }
    }

    /// Replay frames buffered while authentication was in progress, now that the
    /// session bridge exists. Runs on the NIO event loop thread.
    private func flushPendingPreAuthFrames(channel: Channel) {
        guard !pendingPreAuthFrames.isEmpty else { return }
        let frames = pendingPreAuthFrames
        pendingPreAuthFrames.removeAll()
        for jsonData in frames {
            processAuthenticatedFrame(jsonData: jsonData, channel: channel)
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

        Task { @MainActor in
            if let bridge {
                serverRef?.unregisterBridge(bridge)
            }
            if let deviceId {
                RemoteAuditLog.deviceDisconnect(deviceId: deviceId, reason: "connection_closed")
            }
        }
        cachedChannel = nil
    }

    // MARK: - Frame Handling

    func channelRead(context: ChannelHandlerContext, data: NIOAny) {
        let frame = unwrapInboundIn(data)
        #if DEBUG
        HTTPRequestRouter.wsLog("[WSH] channelRead opcode=\(frame.opcode) bytes=\(frame.unmaskedData.readableBytes)")
        #endif

        switch frame.opcode {
        case .text:
            handleTextFrame(frame, channel: context.channel)

        case .binary:
            Logger.remoteControl.debug("RemoteWebSocketHandler: unexpected binary frame from client")

        case .connectionClose:
            handleClose(context: context, frame: frame)

        case .ping:
            let pongFrame = WebSocketFrame(fin: true, opcode: .pong, data: frame.unmaskedData)
            context.writeAndFlush(wrapOutboundOut(pongFrame), promise: nil)

        case .pong:
            break

        default:
            break
        }

        if let ch = cachedChannel {
            resetHeartbeat(channel: ch)
        }
    }

    func errorCaught(context: ChannelHandlerContext, error: Error) {
        #if DEBUG
        HTTPRequestRouter.wsLog("[WSH] errorCaught: \(error.localizedDescription)")
        #endif
        Logger.remoteControl.error(
            "RemoteWebSocketHandler error: \(error.localizedDescription, privacy: .public)"
        )
        context.close(promise: nil)
    }

    // MARK: - Private: Text Frame Processing

    private func handleTextFrame(_ frame: WebSocketFrame, channel: Channel) {
        guard frame.unmaskedData.readableBytes <= 65_536 else {
            Logger.remoteControl.warning(
                "RemoteWebSocketHandler: oversized frame rejected (\(frame.unmaskedData.readableBytes) bytes)"
            )
            return
        }
        var data = frame.unmaskedData
        guard let str = data.readString(length: data.readableBytes),
              let jsonData = str.data(using: .utf8) else {
            return
        }

        struct TypeEnvelope: Decodable {
            let type: String
        }

        guard let envelope = try? decoder.decode(TypeEnvelope.self, from: jsonData) else {
            Logger.remoteControl.warning("RemoteWebSocketHandler: failed to decode message type")
            return
        }

        // SECURITY: First message must be auth. All other messages rejected until authenticated.
        // Both `isAuthenticated` and `authInProgress` are read and written exclusively on the
        // NIO event loop thread (this call stack), closing the race window that existed when only
        // `isAuthenticated` was checked and the flag was set later inside a MainActor Task.
        if !isAuthenticated {
            if envelope.type == "auth" {
                guard !authInProgress else {
                    // A concurrent auth Task is already in flight — ignore the duplicate frame.
                    return
                }
                // Mark auth as in-progress synchronously before any async dispatch so that
                // a second frame arriving on the same event loop iteration cannot also enter here.
                authInProgress = true
                handleAuthMessage(jsonData: jsonData, channel: channel)
            } else if authInProgress {
                // Auth is validating on the MainActor and a non-auth frame
                // (the client's initial `resize`) raced ahead of `auth_ok`.
                // Buffer it for replay rather than dropping the connection.
                if pendingPreAuthFrames.count < Self.maxPendingPreAuthFrames {
                    pendingPreAuthFrames.append(jsonData)
                } else {
                    Logger.remoteControl.warning(
                        "RemoteWebSocketHandler: pre-auth frame buffer full, dropping frame"
                    )
                }
            } else {
                // First frame was not auth and no auth is in flight — a genuine
                // protocol violation. Reject and close.
                sendErrorAndClose(code: 4000, reason: "Authentication required", channel: channel)
            }
            return
        }

        processAuthenticatedFrame(jsonData: jsonData, channel: channel)
    }

    /// Dispatch a frame from an already-authenticated peer. Extracted so that
    /// buffered pre-auth frames can be replayed through the identical code path
    /// once the session bridge exists (see `flushPendingPreAuthFrames`).
    private func processAuthenticatedFrame(jsonData: Data, channel: Channel) {
        struct TypeEnvelope: Decodable {
            let type: String
        }

        guard let envelope = try? decoder.decode(TypeEnvelope.self, from: jsonData) else {
            Logger.remoteControl.warning("RemoteWebSocketHandler: failed to decode message type")
            return
        }

        let bridge = getBridge()

        switch envelope.type {
        case "auth":
            // Already authenticated — ignore duplicate auth messages.
            break

        case "input":
            guard let msg = try? decoder.decode(WSInputMessage.self, from: jsonData) else { return }
            // SECURITY: Limit PTY input to 4096 bytes to prevent oversized payloads from being
            // forwarded directly to the PTY, which could exhaust kernel buffers or trigger bugs
            // in terminal applications relying on bounded line lengths.
            let maxInputBytes = 4096
            guard msg.data.utf8.count <= maxInputBytes else {
                Logger.remoteControl.warning(
                    "RemoteWebSocketHandler: input payload exceeds \(maxInputBytes) bytes, rejected"
                )
                let errPayload = "{\"type\":\"error\",\"message\":\"Input too large (max \(maxInputBytes) bytes)\"}"
                sendTextOnEventLoop(errPayload, channel: channel)
                return
            }
            Task { @MainActor in
                bridge?.handleInput(msg.data)
            }

        case "resize":
            guard let msg = try? decoder.decode(WSResizeMessage.self, from: jsonData) else { return }
            Task { @MainActor in
                bridge?.handleResize(cols: msg.cols, rows: msg.rows)
            }

        case "ping":
            guard let msg = try? decoder.decode(WSPingMessage.self, from: jsonData) else { return }
            let pong = WSPongMessage(
                type: "pong",
                ts: msg.ts,
                serverTs: Int64(Date().timeIntervalSince1970 * 1000)
            )
            if let pongData = try? self.pongEncoder.encode(pong),
               let pongString = String(data: pongData, encoding: .utf8) {
                // Route through the shared helper to keep threading invariants
                // (event-loop dispatch, frame construction) uniform with the
                // other paths (auth ack, input rejection, error envelopes).
                sendTextOnEventLoop(pongString, channel: channel)
            }

        case "detach":
            Task { @MainActor in
                bridge?.detach()
            }

        default:
            Logger.remoteControl.debug("RemoteWebSocketHandler: unknown message type '\(envelope.type)'")
        }
    }

    // MARK: - Private: Auth Message

    /// Handle the first WS message: `{"type":"auth","token":"<jwt>"}`.
    private func handleAuthMessage(jsonData: Data, channel: Channel) {
        struct AuthEnvelope: Decodable {
            let token: String
        }

        guard let authMsg = try? decoder.decode(AuthEnvelope.self, from: jsonData) else {
            sendErrorAndClose(code: 4000, reason: "Invalid auth message format", channel: channel)
            return
        }

        guard let authSvc = authService else {
            sendErrorAndClose(code: 4000, reason: "Auth service unavailable", channel: channel)
            return
        }

        let ip = clientIP
        let token = authMsg.token

        Task { @MainActor [weak self] in
            guard let self else { return }
            let result = authSvc.validateToken(token, clientIP: ip)
            switch result {
            case .success(let device):
                #if DEBUG
                HTTPRequestRouter.wsLog("[WSH] auth OK device=\(device.id) ip=\(ip)")
                #endif
                // SECURITY: All mutations of WS-handler state (deviceInfo, isAuthenticated,
                // authInProgress, _authTimeoutTask) must happen on the NIO event loop thread
                // so they stay consistent with channelRead/handleTextFrame reads.
                channel.eventLoop.execute {
                    self.deviceInfo = device
                    self.isAuthenticated = true
                    self.cancelAuthTimeout()

                    // Send auth success confirmation.
                    let ack = "{\"type\":\"auth_ok\"}"
                    self.sendTextOnEventLoop(ack, channel: channel)

                    self.initializeSession(channel: channel, device: device)
                }

            case .failure(let authError):
                // DIAGNOSTIC: surface *which* validation failed. Re-prompting the
                // user for a PIN remotely is the worst failure mode (they can't see
                // the screen), so we record the precise reason — invalidToken
                // (server restarted / token rotated away), tokenExpired, or
                // ipMismatch (apparent client IP changed) — to pin down the cause.
                let reasonTag: String
                switch authError {
                case .invalidToken: reasonTag = "invalid_token"
                case .tokenExpired: reasonTag = "token_expired"
                case .ipMismatch: reasonTag = "ip_mismatch"
                default: reasonTag = "other"
                }
                Logger.remoteControl.warning(
                    "RemoteWebSocketHandler: WS auth FAILED reason=\(reasonTag, privacy: .public) ip=\(ip, privacy: .public)"
                )
                #if DEBUG
                HTTPRequestRouter.wsLog("[WSH] auth FAILED reason=\(reasonTag) ip=\(ip)")
                #endif
                channel.eventLoop.execute {
                    // Reset authInProgress on the event loop thread so the flag stays
                    // consistent with the thread that originally set it.
                    self.authInProgress = false
                    // Discard any frames buffered against this now-failed auth.
                    self.pendingPreAuthFrames.removeAll()
                    self.sendErrorAndClose(code: 4000, reason: "Authentication failed", channel: channel)
                }
            }
        }
    }

    /// Send a text frame via eventLoop.
    private func sendTextOnEventLoop(_ text: String, channel: Channel) {
        channel.eventLoop.execute {
            var buffer = channel.allocator.buffer(capacity: text.utf8.count)
            buffer.writeString(text)
            let frame = WebSocketFrame(fin: true, opcode: .text, data: buffer)
            channel.writeAndFlush(NIOAny(frame), promise: nil)
        }
    }

    /// Send error and close with custom code. Safe from any thread —
    /// writeAndFlush is hopped onto the channel's event loop.
    private func sendErrorAndClose(code: UInt16, reason: String, channel: Channel) {
        channel.eventLoop.execute {
            var buffer = channel.allocator.buffer(capacity: 2 + reason.utf8.count)
            buffer.writeInteger(code)
            buffer.writeString(reason)
            let frame = WebSocketFrame(fin: true, opcode: .connectionClose, data: buffer)
            channel.writeAndFlush(NIOAny(frame)).whenComplete { _ in
                channel.close(promise: nil)
            }
        }
    }

    // MARK: - Private: Auth Timeout

    /// Close connection if no auth message received within 10 seconds.
    private func startAuthTimeout(channel: Channel) {
        let task = Task { [weak self] in
            try? await Task.sleep(for: .seconds(10))
            guard !Task.isCancelled, let self, !self.isAuthenticated else { return }
            Logger.remoteControl.warning("RemoteWebSocketHandler: auth timeout, closing unauthenticated connection")
            self.sendErrorAndClose(code: 4000, reason: "Auth timeout", channel: channel)
        }
        setAuthTimeoutTask(task)
    }

    /// Atomically set the auth-timeout task, cancelling any prior one.
    private func setAuthTimeoutTask(_ task: Task<Void, Never>) {
        bridgeLock.lock()
        let prev = _authTimeoutTask
        _authTimeoutTask = task
        bridgeLock.unlock()
        prev?.cancel()
    }

    /// Atomically cancel and clear the auth-timeout task.
    private func cancelAuthTimeout() {
        bridgeLock.lock()
        let prev = _authTimeoutTask
        _authTimeoutTask = nil
        bridgeLock.unlock()
        prev?.cancel()
    }

    // MARK: - Private: Close Handling

    private func handleClose(context: ChannelHandlerContext, frame: WebSocketFrame) {
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

    private func getBridge() -> RemoteSessionBridge? {
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

    private func resetHeartbeat(channel: Channel) {
        let task = Task { [weak self] in
            // 120s, not 60s: the web client pings every 30s, but mobile browsers
            // (esp. iOS Safari) throttle/suspend timers when the tab is
            // backgrounded or the phone is locked. A 60s window tripped on a
            // single missed ping during a brief screen-lock; 120s tolerates a
            // couple of missed pings without killing an otherwise-live session.
            try? await Task.sleep(for: .seconds(120))
            guard !Task.isCancelled, self != nil else { return }
            Logger.remoteControl.info("RemoteWebSocketHandler: heartbeat timeout, closing connection")
            channel.eventLoop.execute {
                var buffer = channel.allocator.buffer(capacity: 2 + "Heartbeat timeout".utf8.count)
                buffer.writeInteger(UInt16(4004))
                buffer.writeString("Heartbeat timeout")
                let closeFrame = WebSocketFrame(fin: true, opcode: .connectionClose, data: buffer)
                channel.writeAndFlush(NIOAny(closeFrame)).whenComplete { _ in
                    channel.close(promise: nil)
                }
            }
        }
        setHeartbeatTask(task)
    }

    /// Atomically set the heartbeat task, cancelling any prior one.
    private func setHeartbeatTask(_ task: Task<Void, Never>) {
        bridgeLock.lock()
        let prev = _heartbeatTask
        _heartbeatTask = task
        bridgeLock.unlock()
        prev?.cancel()
    }

    /// Atomically cancel and clear the heartbeat task.
    private func cancelHeartbeat() {
        bridgeLock.lock()
        let prev = _heartbeatTask
        _heartbeatTask = nil
        bridgeLock.unlock()
        prev?.cancel()
    }
}

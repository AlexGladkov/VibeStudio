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
/// 1. **Channel active:** Create a ``RemoteSessionBridge``, register with the server.
/// 2. **Text frames:** Parse JSON, dispatch to bridge (input/resize/ping/detach).
/// 3. **Binary frames:** Not expected from clients -- ignored.
/// 4. **Close frame:** Clean up bridge, unregister from server.
/// 5. **Heartbeat:** If no message for 60s, close with code 4004.
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

    /// The session bridge (created on channel active, MainActor-owned).
    /// Protected by `NSLock` to allow safe reads from the NIO event loop
    /// thread while writes occur from MainActor context.
    private let bridgeLock = NSLock()
    private var _bridge: RemoteSessionBridge?

    /// Cached channel reference (Sendable, unlike ChannelHandlerContext).
    private var cachedChannel: Channel?

    /// The terminal session ID extracted from the URL path.
    private let sessionId: UUID

    /// Device info from the authentication token.
    private let deviceInfo: RemoteDevice

    /// Weak reference to the server for bridge registration.
    private weak var serverRef: RemoteControlServer?

    /// Terminal service for I/O.
    private let terminalService: TerminalService

    /// Idle timeout minutes from preferences.
    private let idleTimeoutMinutes: Int

    /// Heartbeat timer: close connection if no message received within 60 seconds.
    nonisolated(unsafe) private var heartbeatTask: Task<Void, Never>?

    /// JSON decoder.
    private let decoder = JSONDecoder()

    // MARK: - Init

    /// Create a WebSocket handler for a terminal session.
    ///
    /// - Parameters:
    ///   - sessionId: The terminal session to attach to.
    ///   - deviceInfo: The authenticated remote device.
    ///   - serverRef: The server for bridge registration/unregistration.
    ///   - terminalService: Terminal service for PTY I/O.
    ///   - idleTimeoutMinutes: Idle timeout from preferences.
    init(
        sessionId: UUID,
        deviceInfo: RemoteDevice,
        serverRef: RemoteControlServer?,
        terminalService: TerminalService,
        idleTimeoutMinutes: Int
    ) {
        self.sessionId = sessionId
        self.deviceInfo = deviceInfo
        self.serverRef = serverRef
        self.terminalService = terminalService
        self.idleTimeoutMinutes = idleTimeoutMinutes
    }

    // MARK: - Channel Lifecycle

    func handlerAdded(context: ChannelHandlerContext) {
        #if DEBUG
        HTTPRequestRouter.wsLog("[WSH] handlerAdded isActive=\(context.channel.isActive)")
        #endif
        // When added to an already-active pipeline (manual WS upgrade),
        // channelActive is not called. Initialize here instead.
        if context.channel.isActive {
            initializeSession(channel: context.channel)
        }
    }

    func channelActive(context: ChannelHandlerContext) {
        #if DEBUG
        HTTPRequestRouter.wsLog("[WSH] channelActive fired")
        #endif
        // When added during NIO's upgrade flow, channelActive fires after
        // handlerAdded. Guard against double-init.
        if cachedChannel == nil {
            initializeSession(channel: context.channel)
        }
    }

    private func initializeSession(channel: Channel) {
        cachedChannel = channel
        let sessionId = self.sessionId
        let deviceInfo = self.deviceInfo
        let termSvc = self.terminalService
        let idleTimeout = self.idleTimeoutMinutes
        let serverRef = self.serverRef

        #if DEBUG
        HTTPRequestRouter.wsLog("[WSH] initializeSession: session=\(sessionId) device=\(deviceInfo.id)")
        #endif

        Task { @MainActor [weak self] in
            let bridge = RemoteSessionBridge(
                deviceId: deviceInfo.id,
                sessionId: sessionId,
                channel: channel,
                terminalService: termSvc,
                idleTimeoutMinutes: idleTimeout
            )
            self?.setBridge(bridge)
            bridge.startStreaming()
            serverRef?.registerBridge(bridge)
            RemoteAuditLog.deviceConnect(device: deviceInfo, sessionId: sessionId)
        }

        resetHeartbeat(channel: channel)
    }

    func channelInactive(context: ChannelHandlerContext) {
        #if DEBUG
        HTTPRequestRouter.wsLog("[WSH] channelInactive")
        #endif
        heartbeatTask?.cancel()
        heartbeatTask = nil

        let bridge = takeBridge()
        let serverRef = self.serverRef
        let deviceId = deviceInfo.id

        Task { @MainActor in
            if let bridge {
                serverRef?.unregisterBridge(bridge)
            }
            RemoteAuditLog.deviceDisconnect(deviceId: deviceId, reason: "connection_closed")
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
            // Binary frames from client are not expected -- ignore.
            Logger.remoteControl.debug("RemoteWebSocketHandler: unexpected binary frame from client")

        case .connectionClose:
            handleClose(context: context, frame: frame)

        case .ping:
            // Respond with pong.
            let pongFrame = WebSocketFrame(fin: true, opcode: .pong, data: frame.unmaskedData)
            context.writeAndFlush(wrapOutboundOut(pongFrame), promise: nil)

        case .pong:
            // Client responded to our ping -- no action needed.
            break

        default:
            break
        }

        // Reset heartbeat on any message.
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
        // Reject oversized frames (64KB limit for JSON control messages).
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

        // Parse the discriminator "type" field first.
        struct TypeEnvelope: Decodable {
            let type: String
        }

        guard let envelope = try? decoder.decode(TypeEnvelope.self, from: jsonData) else {
            Logger.remoteControl.warning("RemoteWebSocketHandler: failed to decode message type")
            return
        }

        let bridge = getBridge()

        switch envelope.type {
        case "input":
            guard let msg = try? decoder.decode(WSInputMessage.self, from: jsonData) else { return }
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
            if let pongData = try? JSONEncoder().encode(pong),
               let pongString = String(data: pongData, encoding: .utf8) {
                var buffer = channel.allocator.buffer(capacity: pongString.utf8.count)
                buffer.writeString(pongString)
                let responseFrame = WebSocketFrame(fin: true, opcode: .text, data: buffer)
                channel.writeAndFlush(NIOAny(responseFrame), promise: nil)
            }

        case "detach":
            Task { @MainActor in
                bridge?.detach()
            }

        default:
            Logger.remoteControl.debug("RemoteWebSocketHandler: unknown message type '\(envelope.type)'")
        }
    }

    // MARK: - Private: Close Handling

    private func handleClose(context: ChannelHandlerContext, frame: WebSocketFrame) {
        // Echo the close frame back per the WebSocket protocol.
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
        heartbeatTask?.cancel()
        heartbeatTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(60))
            guard !Task.isCancelled else { return }
            // No message received for 60 seconds -- close with idle timeout.
            Logger.remoteControl.info("RemoteWebSocketHandler: heartbeat timeout, closing connection")
            var buffer = channel.allocator.buffer(capacity: 2 + "Heartbeat timeout".utf8.count)
            buffer.writeInteger(UInt16(4004))
            buffer.writeString("Heartbeat timeout")
            let closeFrame = WebSocketFrame(fin: true, opcode: .connectionClose, data: buffer)
            channel.writeAndFlush(NIOAny(closeFrame)).whenComplete { _ in
                channel.close(promise: nil)
            }
        }
    }
}

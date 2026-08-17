// MARK: - RemoteWebSocketHandler+Frames
// Iteration 9 split. Inbound WebSocket frame handling + message dispatch
// extracted from ``RemoteWebSocketHandler`` to keep the primary type body
// under SwiftLint's `type_body_length` budget. Behaviour unchanged — the
// members these methods touch are declared `internal` on the main type
// specifically so this cross-file extension can reach them.
//
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOWebSocket
import OSLog

extension RemoteWebSocketHandler {

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

    // MARK: - Text Frame Processing

    /// Envelope used to peek at the `type` discriminator before full decoding.
    private struct TypeEnvelope: Decodable {
        let type: String
    }

    private func handleTextFrame(_ frame: WebSocketFrame, channel: Channel) {
        guard frame.unmaskedData.readableBytes <= RemoteLimits.maxRequestBodyBytes else {
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

        guard let envelope = try? decoder.decode(TypeEnvelope.self, from: jsonData) else {
            Logger.remoteControl.warning("RemoteWebSocketHandler: failed to decode message type")
            return
        }

        // SECURITY: First message must be auth. All other messages rejected until authenticated.
        // `authInProgress` is read and written exclusively on this NIO event loop thread.
        // `isAuthenticated` is likewise *written* on the event loop — the auth-success path in
        // ``handleAuthMessage`` publishes it via `channel.eventLoop.execute` — and read here on
        // the event loop, so this gate observes a coherent value with no torn read. The only
        // off-loop read is the benign `Bool` guard inside the auth-timeout Task, which is
        // additionally cancelled on success.
        if !isAuthenticated {
            handleUnauthenticatedFrame(type: envelope.type, jsonData: jsonData, channel: channel)
            return
        }

        dispatchAuthenticatedMessage(type: envelope.type, jsonData: jsonData, channel: channel)
    }

    /// First-frame gate: only an `auth` message is accepted before the
    /// connection is authenticated; everything else is refused and closed.
    private func handleUnauthenticatedFrame(type: String, jsonData: Data, channel: Channel) {
        if type == "auth" {
            guard !authInProgress else {
                // A concurrent auth Task is already in flight — ignore the duplicate frame.
                return
            }
            // Mark auth as in-progress synchronously before any async dispatch so that
            // a second frame arriving on the same event loop iteration cannot also enter here.
            authInProgress = true
            handleAuthMessage(jsonData: jsonData, channel: channel)
        } else {
            sendErrorAndClose(code: WSCloseCode.authRequired, reason: "Authentication required", channel: channel)
        }
    }

    /// Dispatch a post-auth message to its per-type under-handler.
    private func dispatchAuthenticatedMessage(type: String, jsonData: Data, channel: Channel) {
        let bridge = getBridge()

        switch type {
        case "auth":
            // Already authenticated — ignore duplicate auth messages.
            break

        case "input":
            handleInputMessage(jsonData: jsonData, channel: channel, bridge: bridge)

        case "resize":
            handleResizeMessage(jsonData: jsonData, bridge: bridge)

        case "ping":
            handlePingMessage(jsonData: jsonData, channel: channel)

        case "detach":
            Task { @MainActor in
                bridge?.detach()
            }

        // MARK: Quick-Action Control Commands

        case "kill":
            handleKillMessage(jsonData: jsonData, bridge: bridge)

        case "pause":
            Task { @MainActor in
                bridge?.handlePause()
            }

        case "rerun":
            Task { @MainActor in
                bridge?.handleRerun(apiKeyResolver: KeychainAPIKeyResolver())
            }

        case "clear":
            Task { @MainActor in
                bridge?.handleClear()
            }

        default:
            Logger.remoteControl.debug("RemoteWebSocketHandler: unknown message type '\(type)'")
        }
    }

    /// Decode and dispatch a `kill` control message.
    private func handleKillMessage(jsonData: Data, bridge: RemoteSessionBridge?) {
        guard let msg = try? decoder.decode(WSKillMessage.self, from: jsonData) else {
            // E11: bad JSON — no-op (malformed control messages are silently ignored).
            return
        }
        Task { @MainActor in
            bridge?.handleKill(force: msg.force)
        }
    }

    /// Forward a bounded PTY input payload to the bridge on the MainActor.
    private func handleInputMessage(jsonData: Data, channel: Channel, bridge: RemoteSessionBridge?) {
        guard let msg = try? decoder.decode(WSInputMessage.self, from: jsonData) else { return }
        // SECURITY: Limit PTY input to 4096 bytes to prevent oversized payloads from being
        // forwarded directly to the PTY, which could exhaust kernel buffers or trigger bugs
        // in terminal applications relying on bounded line lengths.
        let maxInputBytes = RemoteLimits.maxPTYInputBytes
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
    }

    /// Forward a terminal resize to the bridge on the MainActor.
    private func handleResizeMessage(jsonData: Data, bridge: RemoteSessionBridge?) {
        guard let msg = try? decoder.decode(WSResizeMessage.self, from: jsonData) else { return }
        Task { @MainActor in
            bridge?.handleResize(cols: msg.cols, rows: msg.rows)
        }
    }

    /// Answer an application-level `ping` with a `pong` carrying server time.
    private func handlePingMessage(jsonData: Data, channel: Channel) {
        guard let msg = try? decoder.decode(WSPingMessage.self, from: jsonData) else { return }
        let pong = WSPongMessage(
            type: "pong",
            ts: msg.ts,
            serverTs: Int64(Date().timeIntervalSince1970 * 1000)
        )
        if let pongData = try? self.pongEncoder.encode(pong),
           let pongString = String(data: pongData, encoding: .utf8) {
            var buffer = channel.allocator.buffer(capacity: pongString.utf8.count)
            buffer.writeString(pongString)
            let responseFrame = WebSocketFrame(fin: true, opcode: .text, data: buffer)
            channel.writeAndFlush(responseFrame, promise: nil)
        }
    }
}

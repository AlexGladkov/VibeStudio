// MARK: - RemoteWebSocketHandler+Auth
// Iteration 9 split. First-frame authentication + the small text/close writers
// it relies on, extracted from ``RemoteWebSocketHandler`` to keep the primary
// type body under SwiftLint's `type_body_length` budget. Behaviour unchanged —
// including the WS race fixes around `authInProgress` / auth-timeout
// cancellation.
//
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOWebSocket
import OSLog

extension RemoteWebSocketHandler {

    // MARK: - Auth Message

    /// Handle the first WS message: `{"type":"auth","token":"<jwt>"}`.
    func handleAuthMessage(jsonData: Data, channel: Channel) {
        struct AuthEnvelope: Decodable {
            let token: String
        }

        guard let authMsg = try? decoder.decode(AuthEnvelope.self, from: jsonData) else {
            sendErrorAndClose(code: WSCloseCode.authRequired, reason: "Invalid auth message format", channel: channel)
            return
        }

        guard let authSvc = authService else {
            sendErrorAndClose(code: WSCloseCode.authRequired, reason: "Auth service unavailable", channel: channel)
            return
        }

        let ip = clientIP
        let token = authMsg.token

        Task { @MainActor [weak self] in
            guard let self else { return }
            let result = authSvc.validateToken(token, clientIP: ip)
            switch result {
            case .success(let device):
                // MEMORY-SAFETY: `deviceInfo`/`isAuthenticated` are owned by the
                // NIO event loop — they are read there in `handleTextFrame`
                // (the auth gate), `onChannelReady` and `channelInactive`.
                // `deviceInfo` is a multi-word `RemoteDevice?` value, so writing
                // it from the MainActor while the event loop reads it risks a
                // torn read (and thus an over-release / crash). Publish both here
                // on the event loop so there is a single writer.
                //
                // `authInProgress` is reset in the same hop: without this it
                // would stay `true` for the connection's lifetime — harmless
                // today (the auth gate short-circuits on `isAuthenticated`) but
                // it would silently break any future `authInProgress` re-check.
                channel.eventLoop.execute {
                    self.deviceInfo = device
                    self.isAuthenticated = true
                    self.authInProgress = false
                }
                // WS race fix: cancel auth-timeout under the lock to close the
                // window where the timeout task could fire between the
                // `!isAuthenticated` check inside its body and this cancel.
                self.cancelAuthTimeout()

                // Send auth success confirmation. Enqueued on the event loop
                // after the state-publish block above, so `isAuthenticated` is
                // already `true` by the time the client can respond with input.
                let ack = "{\"type\":\"auth_ok\"}"
                self.sendTextOnEventLoop(ack, channel: channel)

                self.initializeSession(channel: channel, device: device)

            case .failure:
                channel.eventLoop.execute {
                    // Reset authInProgress on the event loop thread so the flag stays
                    // consistent with the thread that originally set it.
                    self.authInProgress = false
                    self.sendErrorAndClose(code: WSCloseCode.authRequired, reason: "Authentication failed", channel: channel)
                }
            }
        }
    }

    // MARK: - Frame Writers

    /// Send a text frame via eventLoop.
    func sendTextOnEventLoop(_ text: String, channel: Channel) {
        channel.eventLoop.execute {
            var buffer = channel.allocator.buffer(capacity: text.utf8.count)
            buffer.writeString(text)
            let frame = WebSocketFrame(fin: true, opcode: .text, data: buffer)
            channel.writeAndFlush(NIOAny(frame), promise: nil)
        }
    }

    /// Send error and close with custom code.
    func sendErrorAndClose(code: UInt16, reason: String, channel: Channel) {
        var buffer = channel.allocator.buffer(capacity: 2 + reason.utf8.count)
        buffer.writeInteger(code)
        buffer.writeString(reason)
        let frame = WebSocketFrame(fin: true, opcode: .connectionClose, data: buffer)
        channel.writeAndFlush(NIOAny(frame)).whenComplete { _ in
            channel.close(promise: nil)
        }
    }
}

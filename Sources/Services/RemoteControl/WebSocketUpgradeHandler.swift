// MARK: - WebSocketUpgradeHandler
// A7 split (Iteration 9). Owns the manual RFC 6455 WebSocket upgrade that used
// to live inline in ``HTTPRequestRouter``: SEC-H1 device-cap gate, SEC-H2
// transport-security validation, the SHA-1 `Sec-WebSocket-Accept` handshake,
// the 101 Switching-Protocols response, and the HTTP→WebSocket pipeline swap.
//
// The type is a lightweight `final class` owned strongly by the router. Because
// it is the router's only strong owner, capturing `[weak self]` inside the
// deferred event-loop closure preserves the exact "skip the upgrade if the
// channel handler was torn down" semantics of the original `[weak self]` on the
// router.
//
// macOS 14+, Swift 5.10

import CryptoKit
import Foundation
import NIOCore
import NIOHTTP1
import NIOWebSocket
import OSLog

/// Performs manual WebSocket upgrades for the Remote Control terminal endpoints.
///
/// NIO's `HTTPServerUpgradeHandler` is one-shot and removes itself after the
/// first non-upgrade request, breaking HTTP/1.1 keep-alive connections where
/// the WS upgrade comes after page load. This type handles the upgrade directly.
///
/// **Threading model:** invoked on the NIO event-loop thread. The only hop to
/// `@MainActor` is the SEC-H1 device-cap read, which bounces back to the event
/// loop before touching the channel pipeline.
final class WebSocketUpgradeHandler {

    // MARK: - Dependencies (immutable)

    private let writer: HTTPResponseWriter
    private let authService: RemoteAuthService
    private let terminalService: TerminalService
    private weak var serverRef: RemoteControlServer?
    private let idleTimeoutMinutes: Int

    // MARK: - Init

    init(
        writer: HTTPResponseWriter,
        authService: RemoteAuthService,
        terminalService: TerminalService,
        serverRef: RemoteControlServer?,
        idleTimeoutMinutes: Int
    ) {
        self.writer = writer
        self.authService = authService
        self.terminalService = terminalService
        self.serverRef = serverRef
        self.idleTimeoutMinutes = idleTimeoutMinutes
    }

    // MARK: - Upgrade entry point

    /// Validate the session id, apply the SEC-H1 device cap, then perform the
    /// handshake. `clientIP` is the real TCP-level remote address captured at
    /// `channelActive` time (never a spoofable header).
    func handleUpgrade(
        head: HTTPRequestHead,
        path: String,
        channel: Channel,
        corsOrigin: String?,
        clientIP: String
    ) {
        let sessionIdStr = String(path.dropFirst("/api/v1/terminal/".count))
        guard let sessionId = UUID(uuidString: sessionIdStr) else {
            writer.sendErrorJSON(
                status: .badRequest, code: "INVALID_SESSION",
                message: "Invalid session ID.",
                channel: channel, corsOrigin: corsOrigin
            )
            return
        }

        // SEC-H1: reject WS upgrade when at device cap. We read
        // `activeBridges.count` via the server reference; if no server is
        // wired in (preview/test) we skip the check.
        if let server = serverRef {
            // serverRef is @MainActor — hop briefly to read state, then bounce
            // back to the event loop for the upgrade.
            let theWriter = writer
            Task { @MainActor in
                let atCapacity = server.activeBridges.count >= RemoteAuthService.maxDevices
                if atCapacity {
                    channel.eventLoop.execute {
                        theWriter.sendErrorJSON(
                            status: .serviceUnavailable,
                            code: "MAX_DEVICES_REACHED",
                            message: "Maximum number of connected devices reached. Try again later.",
                            channel: channel,
                            corsOrigin: corsOrigin
                        )
                    }
                    return
                }
                channel.eventLoop.execute { [weak self] in
                    self?.performUpgrade(
                        head: head, sessionId: sessionId, channel: channel,
                        corsOrigin: corsOrigin, clientIP: clientIP
                    )
                }
            }
            return
        }

        performUpgrade(
            head: head, sessionId: sessionId, channel: channel,
            corsOrigin: corsOrigin, clientIP: clientIP
        )
    }

    // MARK: - Handshake

    private func performUpgrade(
        head: HTTPRequestHead,
        sessionId: UUID,
        channel: Channel,
        corsOrigin: String?,
        clientIP: String
    ) {
        guard let wsKey = head.headers["Sec-WebSocket-Key"].first else {
            writer.sendErrorJSON(
                status: .badRequest, code: "INVALID_REQUEST",
                message: "Missing Sec-WebSocket-Key.",
                channel: channel, corsOrigin: corsOrigin
            )
            return
        }

        guard validateTransportSecurity(
            head: head, channel: channel, corsOrigin: corsOrigin, clientIP: clientIP
        ) else {
            return
        }

        let requestedProtocol = head.headers["Sec-WebSocket-Protocol"].first

        #if DEBUG
        HTTPRequestRouter.wsLog("[WS] handleWebSocketUpgrade: session=\(sessionId) ip=\(clientIP)")
        #endif

        let acceptValue = Self.computeWebSocketAccept(key: wsKey)

        let wsHandler = RemoteWebSocketHandler(
            sessionId: sessionId,
            deviceInfo: nil, // auth on first WS frame
            serverRef: serverRef,
            terminalService: terminalService,
            idleTimeoutMinutes: idleTimeoutMinutes,
            authService: authService,
            clientIP: clientIP
        )

        sendHandshakeResponse(
            channel: channel,
            acceptValue: acceptValue,
            requestedProtocol: requestedProtocol,
            handler: wsHandler
        )
    }

    /// SEC-H2: reject the upgrade unless the transport is trustworthy.
    ///
    /// The server itself binds plain HTTP (TLS is terminated upstream when
    /// accessed via the ngrok HTTPS tunnel, or skipped entirely on
    /// loopback — see RemoteConnectionURLBuilder). Without TLS in front of
    /// us, an attacker with on-path access to a non-loopback hop could
    /// sniff/inject the auth token transported as the first WS text frame
    /// and hijack the session.
    ///
    /// Loopback (127.0.0.1 / ::1) is considered safe — no on-path
    /// attacker. For any other client IP we require evidence that an
    /// HTTPS hop terminated TLS upstream. ngrok always forwards the
    /// client-facing scheme in `X-Forwarded-Proto`; if it is missing or
    /// says "http", we refuse the upgrade with 403 so an honest client
    /// sees the failure and reconnects via wss://.
    ///
    /// - Returns: `true` when the upgrade may proceed; `false` after an error
    ///   response has already been written.
    private func validateTransportSecurity(
        head: HTTPRequestHead,
        channel: Channel,
        corsOrigin: String?,
        clientIP: String
    ) -> Bool {
        guard !Self.isTransportTrusted(clientIP: clientIP, head: head) else { return true }

        let proto = Self.resolveForwardedProto(head) ?? "missing"
        let logMsg = "WS upgrade rejected: non-loopback client without HTTPS terminator "
            + "(ip=\(clientIP) proto=\(proto))"
        Logger.remoteControl.warning("\(logMsg, privacy: .public)")
        writer.sendErrorJSON(
            status: .forbidden, code: "TLS_REQUIRED",
            message: "WebSocket connections from non-loopback clients must be made over wss://.",
            channel: channel, corsOrigin: corsOrigin
        )
        return false
    }

    /// Resolve the client-facing scheme an upstream TLS terminator forwarded.
    ///
    /// Prefers the `X-Forwarded-Proto` header (set by ngrok and most reverse
    /// proxies); falls back to the RFC 7239 `Forwarded: proto="https"` form.
    /// Comparison is case-insensitive and quotes are stripped from the RFC 7239
    /// value. Returns `nil` when neither header is present.
    ///
    /// Pure function — no channel/IO side effects — so it is unit-testable in
    /// isolation from ``validateTransportSecurity``.
    static func resolveForwardedProto(_ head: HTTPRequestHead) -> String? {
        head.headers["X-Forwarded-Proto"].first?.lowercased()
            ?? head.headers["Forwarded"].first?
                .split(separator: ";")
                .map { $0.trimmingCharacters(in: .whitespaces) }
                .first(where: { $0.lowercased().hasPrefix("proto=") })
                .map { String($0.dropFirst("proto=".count))
                    .trimmingCharacters(in: CharacterSet(charactersIn: "\"")).lowercased() }
    }

    /// SEC-H2 trust predicate: the transport is trustworthy when the client is
    /// on loopback (no on-path attacker) or an upstream hop terminated TLS and
    /// forwarded `proto=https`.
    ///
    /// Pure function — unit-testable without a live channel.
    static func isTransportTrusted(clientIP: String, head: HTTPRequestHead) -> Bool {
        if LoopbackCheck.isLoopback(clientIP) { return true }
        return resolveForwardedProto(head) == "https"
    }

    /// Compute the `Sec-WebSocket-Accept` value per RFC 6455 §4.2.2.
    static func computeWebSocketAccept(key: String) -> String {
        let magicGUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        let hash = Insecure.SHA1.hash(data: Data((key + magicGUID).utf8))
        return Data(hash).base64EncodedString()
    }

    /// Write the 101 Switching Protocols handshake on the event loop, then
    /// swap in the WebSocket pipeline once the response has flushed.
    private func sendHandshakeResponse(
        channel: Channel,
        acceptValue: String,
        requestedProtocol: String?,
        handler: RemoteWebSocketHandler
    ) {
        channel.eventLoop.execute {
            // Pause reads so incoming WS frames don't hit the HTTP decoder
            // during the pipeline swap.
            channel.setOption(ChannelOptions.autoRead, value: false).whenFailure { _ in }

            var headers = HTTPHeaders()
            headers.add(name: "Upgrade", value: "websocket")
            headers.add(name: "Connection", value: "upgrade")
            headers.add(name: "Sec-WebSocket-Accept", value: acceptValue)
            // Only echo "vibestudio.v1" back — never arbitrary client-supplied protocol.
            if requestedProtocol == "vibestudio.v1" {
                headers.add(name: "Sec-WebSocket-Protocol", value: "vibestudio.v1")
            }

            let responseHead = HTTPResponseHead(
                version: .http1_1, status: .switchingProtocols, headers: headers
            )
            channel.write(NIOAny(HTTPServerResponsePart.head(responseHead)), promise: nil)
            channel.writeAndFlush(NIOAny(HTTPServerResponsePart.end(nil))).whenSuccess {
                self.installPipeline(channel: channel, handler: handler)
            }
        }
    }

    /// Replace the HTTP pipeline with the WebSocket frame codec + handler.
    private func installPipeline(channel: Channel, handler: RemoteWebSocketHandler) {
        #if DEBUG
        HTTPRequestRouter.wsLog("[WS] installWebSocketPipeline: starting pipeline swap")
        #endif

        let el = channel.eventLoop
        let pipeline = channel.pipeline

        func removeByName(_ name: String) -> EventLoopFuture<Void> {
            pipeline.removeHandler(name: name).flatMapError { _ in
                el.makeSucceededVoidFuture()
            }
        }

        removeByName("http_router").flatMap { () -> EventLoopFuture<Void> in
            #if DEBUG
            HTTPRequestRouter.wsLog("[WS] step1: router removed")
            #endif
            return removeByName("http_error")
        }.flatMap { () -> EventLoopFuture<Void> in
            #if DEBUG
            HTTPRequestRouter.wsLog("[WS] step2: error handler removed")
            #endif
            return removeByName("http_decoder")
        }.flatMap { () -> EventLoopFuture<Void> in
            #if DEBUG
            HTTPRequestRouter.wsLog("[WS] step3: decoder removed")
            #endif
            return removeByName("http_encoder")
        }.flatMap { () -> EventLoopFuture<Void> in
            #if DEBUG
            HTTPRequestRouter.wsLog("[WS] step4: encoder removed — adding WS handlers")
            #endif
            return pipeline.addHandler(WebSocketFrameEncoder())
        }.flatMap { () -> EventLoopFuture<Void> in
            pipeline.addHandler(ByteToMessageHandler(WebSocketFrameDecoder()))
        }.flatMap { () -> EventLoopFuture<Void> in
            pipeline.addHandler(handler)
        }.whenComplete { result in
            switch result {
            case .success:
                #if DEBUG
                HTTPRequestRouter.wsLog("[WS] pipeline swap COMPLETE — handler installed")
                #endif
                channel.setOption(ChannelOptions.autoRead, value: true).whenSuccess {
                    channel.read()
                }
            case .failure(let error):
                #if DEBUG
                HTTPRequestRouter.wsLog("[WS] pipeline swap FAILED: \(error)")
                #endif
                channel.close(promise: nil)
            }
        }
    }

}

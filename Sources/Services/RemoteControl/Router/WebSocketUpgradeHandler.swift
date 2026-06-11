// MARK: - WebSocketUpgradeHandler
// Manual WebSocket upgrade (RFC 6455) and pipeline swap.
// macOS 14+, Swift 5.10

import CryptoKit
import Foundation
import NIOCore
import NIOHTTP1
import NIOWebSocket

/// Manual WebSocket upgrade orchestrator (RFC 6455).
///
/// NIO's stock `HTTPServerUpgradeHandler` is one-shot — it removes itself from
/// the pipeline after the first non-upgrade request. On HTTP/1.1 keep-alive
/// connections (Safari's default), the HTML/CSS/JS/REST requests arrive first,
/// the upgrader is gone, and the WebSocket upgrade falls through to the router
/// as a plain GET. This helper performs the upgrade directly.
///
/// **Responsibilities:**
/// - Compute `Sec-WebSocket-Accept` from the client key.
/// - Build the 101 Switching Protocols headers (echoing `vibestudio.v1` only).
/// - Swap the HTTP pipeline for the WebSocket frame codec + handler.
// Note: intentionally NOT Sendable — `installWebSocketPipeline` captures a
// non-Sendable `RemoteWebSocketHandler` (NIO ChannelHandler class) inside
// EventLoopFuture.flatMap closures that NIO types as `@Sendable`. Marking the
// struct Sendable would propagate that violation. All call sites use the
// handler on the channel's event loop only.
struct WebSocketUpgradeHandler {

    /// Result of preparing the upgrade response.
    struct UpgradeResponse {
        let headers: HTTPHeaders
        let acceptValue: String
    }

    /// Outcome of parsing a terminal WebSocket request URL.
    enum SessionParseResult {
        case ok(UUID)
        case invalidSession
        case missingKey
    }

    /// Parse the session UUID embedded in the request path.
    /// Both `/ws/terminal/{id}` (mobile client) and the legacy
    /// `/api/v1/terminal/{id}` path are accepted.
    func parseSession(path: String, head: HTTPRequestHead) -> SessionParseResult {
        let prefix = path.hasPrefix("/ws/terminal/") ? "/ws/terminal/" : "/api/v1/terminal/"
        let sessionIdStr = String(path.dropFirst(prefix.count))
        guard let sessionId = UUID(uuidString: sessionIdStr) else {
            return .invalidSession
        }
        guard head.headers["Sec-WebSocket-Key"].first != nil else {
            return .missingKey
        }
        return .ok(sessionId)
    }

    /// Build the 101 Switching Protocols headers for the given client key.
    /// Echoes `Sec-WebSocket-Protocol` only when the requested subprotocol
    /// is exactly `vibestudio.v1` (prevents subprotocol spoofing).
    func buildUpgradeResponse(
        secWebSocketKey: String,
        requestedProtocol: String?
    ) -> UpgradeResponse {
        let magicGUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        let hash = Insecure.SHA1.hash(data: Data((secWebSocketKey + magicGUID).utf8))
        let acceptValue = Data(hash).base64EncodedString()

        var headers = HTTPHeaders()
        headers.add(name: "Upgrade", value: "websocket")
        headers.add(name: "Connection", value: "upgrade")
        headers.add(name: "Sec-WebSocket-Accept", value: acceptValue)
        if requestedProtocol == "vibestudio.v1" {
            headers.add(name: "Sec-WebSocket-Protocol", value: "vibestudio.v1")
        }
        return UpgradeResponse(headers: headers, acceptValue: acceptValue)
    }

    /// Replace the HTTP pipeline with the WebSocket frame codec + the supplied
    /// `RemoteWebSocketHandler`. Pauses reads during the swap to avoid a race
    /// where the first WS frame hits the (now-removed) HTTP decoder.
    func installWebSocketPipeline(channel: Channel, handler: RemoteWebSocketHandler) {
        #if DEBUG
        HTTPRequestRouter.wsLog("[WS] installWebSocketPipeline: starting pipeline swap")
        #endif

        let el = channel.eventLoop
        let pipeline = channel.pipeline

        // Helper: remove a handler by name, ignoring "not found" errors.
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

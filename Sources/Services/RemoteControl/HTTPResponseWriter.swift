// MARK: - HTTPResponseWriter
// ARCH-H1 + ARCH-H9: Stateless response builder for the Remote Control
// HTTP layer. Extracted from HTTPRequestRouter so the router can focus on
// dispatch logic. `corsOrigin` is passed in per-call (never stored on the
// instance) which eliminates the previous race window where a Task-deferred
// response could pick up the *next* request's origin on a keep-alive socket.
//
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOHTTP1

/// Stateless helper that writes HTTP responses to a NIO ``Channel``.
///
/// All methods are `nonisolated` and take an explicit `corsOrigin: String?`
/// argument: the caller resolves the safe origin at request entry and
/// threads it through. Nothing about the response depends on per-instance
/// state.
struct HTTPResponseWriter: Sendable {

    // MARK: - Outbound type bridging
    //
    // NIO requires writes via `NIOAny.wrapOutboundOut`; that helper is only
    // available on a `ChannelOutboundInvoker` typed with the matching outbound
    // type. We wrap the writes manually so callers can hand us any `Channel`.

    private static func writeHead(_ channel: Channel, _ head: HTTPResponseHead) {
        channel.write(NIOAny(HTTPServerResponsePart.head(head)), promise: nil)
    }

    private static func writeBody(_ channel: Channel, _ buffer: ByteBuffer) {
        channel.write(NIOAny(HTTPServerResponsePart.body(.byteBuffer(buffer))), promise: nil)
    }

    private static func writeEnd(_ channel: Channel) {
        channel.writeAndFlush(NIOAny(HTTPServerResponsePart.end(nil)), promise: nil)
    }

    // MARK: - JSON encoder

    /// Shared JSON encoder configured for ISO-8601 dates.
    let encoder: JSONEncoder

    init(encoder: JSONEncoder) {
        self.encoder = encoder
    }

    // MARK: - Security headers

    /// Append security headers common to all responses.
    ///
    /// - Parameters:
    ///   - headers: Mutable header set to augment.
    ///   - isHTML: When `true`, emits the web-UI Content-Security-Policy
    ///     (inline styles + WebSocket); otherwise emits a tight
    ///     `default-src 'none'` policy suitable for JSON API responses.
    func addSecurityHeaders(to headers: inout HTTPHeaders, isHTML: Bool) {
        headers.add(name: "X-Content-Type-Options", value: "nosniff")
        headers.add(name: "X-Frame-Options", value: "DENY")
        headers.add(name: "Referrer-Policy", value: "no-referrer")
        // SEC-L2: pin clients to HTTPS for a year so a downgrade-MitM cannot
        // strip TLS on subsequent visits.
        headers.add(name: "Strict-Transport-Security", value: "max-age=31536000")
        if isHTML {
            // SEC-M2: connect-src tightened from `wss: ws:` (any host) to
            // `'self'` only. Browsers treat `connect-src 'self'` as matching
            // the same scheme+host+port as the document for WebSocket
            // upgrades too, so the web UI can still open WS/WSS to the
            // server that served the page while an XSS payload can no
            // longer exfiltrate over a WebSocket to an attacker host.
            headers.add(
                name: "Content-Security-Policy",
                value: "default-src 'self'; " +
                       "script-src 'self'; " +
                       "style-src 'self' 'unsafe-inline'; " +
                       "connect-src 'self'"
            )
        } else {
            headers.add(name: "Content-Security-Policy", value: "default-src 'none'")
        }
    }

    // MARK: - JSON responses

    /// Send a JSON HTTP response with pre-encoded body bytes.
    ///
    /// - Parameters:
    ///   - status: HTTP response status.
    ///   - data: Pre-encoded JSON body.
    ///   - channel: Target NIO channel.
    ///   - corsOrigin: Safe origin to echo back (`nil` to omit CORS headers).
    ///   - retryAfterSeconds: When non-nil, emits `Retry-After` (RFC 7231 §7.1.3).
    func sendRawJSON(
        status: HTTPResponseStatus,
        data: Data,
        channel: Channel,
        corsOrigin: String?,
        retryAfterSeconds: Int? = nil
    ) {
        var headers = HTTPHeaders()
        headers.add(name: "Content-Type", value: "application/json; charset=utf-8")
        headers.add(name: "Content-Length", value: "\(data.count)")
        headers.add(name: "API-Version", value: "1.0.0")
        headers.add(name: "Cache-Control", value: "no-store")
        if let retryAfterSeconds, retryAfterSeconds > 0 {
            headers.add(name: "Retry-After", value: "\(retryAfterSeconds)")
        }
        addSecurityHeaders(to: &headers, isHTML: false)
        if let origin = corsOrigin {
            headers.add(name: "Access-Control-Allow-Origin", value: origin)
            headers.add(name: "Vary", value: "Origin")
            headers.add(name: "Access-Control-Allow-Headers", value: "Authorization, Content-Type")
        }

        let head = HTTPResponseHead(version: .http1_1, status: status, headers: headers)
        var buffer = channel.allocator.buffer(capacity: data.count)
        buffer.writeBytes(data)

        Self.writeHead(channel, head)
        Self.writeBody(channel, buffer)
        Self.writeEnd(channel)
    }

    /// Encode `value` and send as JSON. Silently returns when encoding fails.
    func sendEncodable<T: Encodable>(
        _ value: T,
        status: HTTPResponseStatus,
        channel: Channel,
        corsOrigin: String?
    ) {
        guard let data = try? encoder.encode(value) else { return }
        sendRawJSON(status: status, data: data, channel: channel, corsOrigin: corsOrigin)
    }

    /// Encode `value` on the event loop. Mirrors the
    /// `encode → execute → sendRawJSON` pattern used by MainActor handlers.
    func sendEncodableResponse<T: Encodable>(
        _ value: T,
        status: HTTPResponseStatus,
        channel: Channel,
        corsOrigin: String?
    ) {
        guard let data = try? encoder.encode(value) else { return }
        let writer = self
        channel.eventLoop.execute {
            writer.sendRawJSON(status: status, data: data, channel: channel, corsOrigin: corsOrigin)
        }
    }

    /// Send a JSON error payload.
    func sendErrorJSON(
        status: HTTPResponseStatus,
        code: String,
        message: String,
        channel: Channel,
        corsOrigin: String?
    ) {
        let resp = ErrorResponse(error: ErrorDetail(code: code, message: message))
        guard let data = try? encoder.encode(resp) else { return }
        sendRawJSON(status: status, data: data, channel: channel, corsOrigin: corsOrigin)
    }

    // MARK: - Empty / CORS responses

    /// Send a body-less response.
    func sendEmptyResponse(
        status: HTTPResponseStatus,
        channel: Channel,
        corsOrigin: String?
    ) {
        var headers = HTTPHeaders()
        headers.add(name: "Content-Length", value: "0")
        if let origin = corsOrigin {
            headers.add(name: "Access-Control-Allow-Origin", value: origin)
            headers.add(name: "Vary", value: "Origin")
        }
        let head = HTTPResponseHead(version: .http1_1, status: status, headers: headers)
        Self.writeHead(channel, head)
        Self.writeEnd(channel)
    }

    /// Send a CORS pre-flight (204) response.
    func sendCORSPreflight(channel: Channel, corsOrigin: String?) {
        var headers = HTTPHeaders()
        if let origin = corsOrigin {
            headers.add(name: "Access-Control-Allow-Origin", value: origin)
            headers.add(name: "Vary", value: "Origin")
            headers.add(name: "Access-Control-Allow-Methods", value: "GET, POST, DELETE, OPTIONS")
            headers.add(name: "Access-Control-Allow-Headers", value: "Authorization, Content-Type, X-Request-Id")
            headers.add(name: "Access-Control-Max-Age", value: "86400")
        }
        headers.add(name: "Content-Length", value: "0")

        let head = HTTPResponseHead(version: .http1_1, status: .noContent, headers: headers)
        Self.writeHead(channel, head)
        Self.writeEnd(channel)
    }

    // MARK: - Static file responses

    /// Write a static file payload with the appropriate cache + CSP headers.
    ///
    /// - Parameter isVendor: When true (e.g. `/vendor/xterm.js`), emits an
    ///   immutable 1-year cache; otherwise `no-cache` for dev convenience.
    func sendStaticFileData(
        data: Data,
        contentType: String,
        isVendor: Bool,
        isHTML: Bool,
        channel: Channel
    ) {
        var headers = HTTPHeaders()
        headers.add(name: "Content-Type", value: contentType)
        headers.add(name: "Content-Length", value: "\(data.count)")
        headers.add(
            name: "Cache-Control",
            value: isVendor ? "public, max-age=31536000, immutable" : "no-cache"
        )
        addSecurityHeaders(to: &headers, isHTML: isHTML)

        let head = HTTPResponseHead(version: .http1_1, status: .ok, headers: headers)
        var buffer = channel.allocator.buffer(capacity: data.count)
        buffer.writeBytes(data)

        Self.writeHead(channel, head)
        Self.writeBody(channel, buffer)
        Self.writeEnd(channel)
    }
}

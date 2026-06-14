// MARK: - HTTPResponseBuilder
// Pure response composition for the Remote Control HTTP API.
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOHTTP1

/// Pure-function builder for the standard HTTP responses emitted by the
/// Remote Control HTTP server. Builder *never* touches the channel — it only
/// composes `(HTTPResponseHead, ByteBuffer)` pairs. The calling site writes
/// the pair to the channel.
///
/// Buffers are allocated with the caller's `ByteBufferAllocator` so they
/// share the channel's underlying pool.
struct HTTPResponseBuilder: Sendable {

    /// Encoder used by the `encodableResponse` and `errorJSONResponse` helpers.
    /// Configured once at init for ISO-8601 dates to match the API contract.
    let encoder: JSONEncoder

    init(encoder: JSONEncoder) {
        self.encoder = encoder
    }

    // MARK: - JSON

    func jsonResponse(
        status: HTTPResponseStatus,
        body: Data,
        corsOrigin: String?,
        allocator: ByteBufferAllocator
    ) -> (HTTPResponseHead, ByteBuffer) {
        var headers = HTTPHeaders()
        headers.add(name: "Content-Type", value: "application/json; charset=utf-8")
        headers.add(name: "Content-Length", value: "\(body.count)")
        headers.add(name: "API-Version", value: "1.0.0")
        headers.add(name: "Cache-Control", value: "no-store")
        Self.addSecurityHeaders(to: &headers, isHTML: false)
        if let origin = corsOrigin {
            headers.add(name: "Access-Control-Allow-Origin", value: origin)
            headers.add(name: "Vary", value: "Origin")
            headers.add(name: "Access-Control-Allow-Headers", value: "Authorization, Content-Type")
        }
        let head = HTTPResponseHead(version: .http1_1, status: status, headers: headers)
        var buffer = allocator.buffer(capacity: body.count)
        buffer.writeBytes(body)
        return (head, buffer)
    }

    /// Encode `value` as JSON and build the response. Returns `nil` when
    /// encoding fails — the caller should drop the request silently to
    /// preserve previous behaviour.
    func encodableResponse<T: Encodable>(
        status: HTTPResponseStatus,
        value: T,
        corsOrigin: String?,
        allocator: ByteBufferAllocator
    ) -> (HTTPResponseHead, ByteBuffer)? {
        guard let data = try? encoder.encode(value) else { return nil }
        return jsonResponse(status: status, body: data, corsOrigin: corsOrigin, allocator: allocator)
    }

    /// Build a standard `{ error: { code, message } }` JSON response.
    func errorJSONResponse(
        status: HTTPResponseStatus,
        code: String,
        message: String,
        corsOrigin: String?,
        allocator: ByteBufferAllocator
    ) -> (HTTPResponseHead, ByteBuffer) {
        let resp = ErrorResponse(error: ErrorDetail(code: code, message: message))
        if let data = try? encoder.encode(resp) {
            return jsonResponse(status: status, body: data, corsOrigin: corsOrigin, allocator: allocator)
        }
        // Encoder cannot reasonably fail for this static shape, but if it does,
        // emit an empty body with the requested status so we never hang.
        return emptyResponse(status: status, corsOrigin: corsOrigin, allocator: allocator)
    }

    // MARK: - Empty / Preflight

    func emptyResponse(
        status: HTTPResponseStatus,
        corsOrigin: String?,
        allocator: ByteBufferAllocator
    ) -> (HTTPResponseHead, ByteBuffer) {
        var headers = HTTPHeaders()
        headers.add(name: "Content-Length", value: "0")
        if let origin = corsOrigin {
            headers.add(name: "Access-Control-Allow-Origin", value: origin)
            headers.add(name: "Vary", value: "Origin")
        }
        let head = HTTPResponseHead(version: .http1_1, status: status, headers: headers)
        let buffer = allocator.buffer(capacity: 0)
        return (head, buffer)
    }

    func corsPreflightResponse(
        corsOrigin: String?,
        allocator: ByteBufferAllocator
    ) -> (HTTPResponseHead, ByteBuffer) {
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
        let buffer = allocator.buffer(capacity: 0)
        return (head, buffer)
    }

    // MARK: - Static Files

    /// Build a static-file response with the appropriate cache and CSP headers.
    /// HTML files receive the web-UI CSP (allows inline styles + WebSocket);
    /// everything else gets the restrictive API CSP.
    func staticFileResponse(
        data: Data,
        contentType: String,
        fileName: String,
        allocator: ByteBufferAllocator
    ) -> (HTTPResponseHead, ByteBuffer) {
        var headers = HTTPHeaders()
        headers.add(name: "Content-Type", value: contentType)
        headers.add(name: "Content-Length", value: "\(data.count)")
        let isVendor = fileName.hasPrefix("vendor/")
        headers.add(name: "Cache-Control", value: isVendor
            ? "public, max-age=31536000, immutable"
            : "no-cache"
        )
        if fileName == "sw.js" {
            // CRITICAL: the CSP on the service-worker SCRIPT governs the worker's
            // own runtime, not just the document. `default-src 'none'` implies
            // `connect-src 'none'`, which silently blocked EVERY fetch the SW
            // made — both `cache.add()` at install and runtime revalidation —
            // leaving the cache empty so returning users were served the SW's
            // "Offline" 503 fallback even though the server was reachable. The
            // worker only needs to fetch same-origin shell assets.
            headers.add(name: "X-Content-Type-Options", value: "nosniff")
            headers.add(name: "Referrer-Policy", value: "no-referrer")
            headers.add(
                name: "Content-Security-Policy",
                value: "default-src 'none'; connect-src 'self'"
            )
        } else {
            let isHTML = contentType.hasPrefix("text/html")
            Self.addSecurityHeaders(to: &headers, isHTML: isHTML)
        }
        let head = HTTPResponseHead(version: .http1_1, status: .ok, headers: headers)
        var buffer = allocator.buffer(capacity: data.count)
        buffer.writeBytes(data)
        return (head, buffer)
    }

    /// Build a 413 Payload Too Large response that signals the connection
    /// will be closed.
    func payloadTooLargeResponse(
        allocator: ByteBufferAllocator
    ) -> (HTTPResponseHead, ByteBuffer) {
        let json = #"{"error":{"code":"PAYLOAD_TOO_LARGE","message":"Request body exceeds the 64 KB limit."}}"#
        let data = Data(json.utf8)
        var headers = HTTPHeaders()
        headers.add(name: "Content-Type", value: "application/json; charset=utf-8")
        headers.add(name: "Content-Length", value: "\(data.count)")
        headers.add(name: "Connection", value: "close")
        let head = HTTPResponseHead(
            version: .http1_1,
            status: .payloadTooLarge,
            headers: headers
        )
        var buffer = allocator.buffer(capacity: data.count)
        buffer.writeBytes(data)
        return (head, buffer)
    }

    // MARK: - Security Headers

    /// Append security headers common to all responses.
    ///
    /// - Parameter isHTML: When `true`, emit a CSP tuned for the web UI
    ///   (allows inline styles, WebSocket connections). When `false`,
    ///   emit a restrictive `default-src 'none'` policy.
    static func addSecurityHeaders(to headers: inout HTTPHeaders, isHTML: Bool) {
        headers.add(name: "X-Content-Type-Options", value: "nosniff")
        headers.add(name: "X-Frame-Options", value: "DENY")
        headers.add(name: "Referrer-Policy", value: "no-referrer")
        if isHTML {
            headers.add(
                name: "Content-Security-Policy",
                value: "default-src 'self'; " +
                       "script-src 'self'; " +
                       "style-src 'self' 'unsafe-inline'; " +
                       // `data:` images: the UI renders inline SVG icons (e.g.
                       // the dropdown chevron) as data: URIs. Without this they
                       // are blocked by default-src and the icons vanish.
                       "img-src 'self' data:; " +
                       "connect-src 'self' wss: ws:"
            )
        } else {
            headers.add(name: "Content-Security-Policy", value: "default-src 'none'")
        }
    }
}

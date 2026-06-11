// MARK: - CORSPolicy
// Resolves the safe `Access-Control-Allow-Origin` value for a request.
// macOS 14+, Swift 5.10

import Foundation
import NIOHTTP1

/// Computes the `Access-Control-Allow-Origin` value for an HTTP request.
///
/// **Allowlist policy:**
/// - Reflect `Origin` only when its host is `localhost`, `127.0.0.1`, or `[::1]`
///   on any port (the server's own Web UI), or
/// - The exact ngrok host currently active (no wildcard).
///
/// Threading: `NgrokHostRef` is `Sendable`. This struct is value-typed and may
/// be read concurrently from NIO event loop threads.
struct CORSPolicy: Sendable {

    /// Set of bare hosts treated as same-origin for the embedded server.
    let allowedHosts: Set<String>

    /// Shared reference to the current ngrok hostname (nil when not running).
    let ngrokHostRef: NgrokHostRef

    init(
        allowedHosts: Set<String> = ["localhost", "127.0.0.1", "[::1]"],
        ngrokHostRef: NgrokHostRef
    ) {
        self.allowedHosts = allowedHosts
        self.ngrokHostRef = ngrokHostRef
    }

    /// Resolve the value to echo back in `Access-Control-Allow-Origin`, or
    /// `nil` when the request has no `Origin` header or the origin is not
    /// allowed.
    ///
    /// SECURITY: The previous broad `*.ngrok-free.app` wildcard is replaced
    /// with an exact-host match: only the specific subdomain obtained from
    /// the ngrok local API is allowed.
    func allowedOrigin(from head: HTTPRequestHead) -> String? {
        guard let origin = head.headers["Origin"].first,
              let url = URL(string: origin),
              let host = url.host else {
            // No Origin header — same-origin or non-browser request.
            return nil
        }
        if allowedHosts.contains(host) { return origin }
        if let ngrokHost = ngrokHostRef.host, host == ngrokHost {
            return origin
        }
        return nil
    }
}

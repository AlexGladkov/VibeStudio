// MARK: - CORSPolicy
// A7 split (Iteration 9). Extracts the Remote Control CORS origin policy from
// ``HTTPRequestRouter``. Keeps the allow-list (loopback hosts + the exact
// current ngrok host) and the "reflect the Origin header only when trusted"
// logic in one auditable place. Preflight *responses* are still emitted by
// ``HTTPResponseWriter.sendCORSPreflight`` — this type only decides which
// origin (if any) may be reflected.
//
// macOS 14+, Swift 5.10

import Foundation
import NIOHTTP1

/// Resolves a safe CORS origin for an inbound request.
///
/// The origin is reflected only when it matches `localhost` / `127.0.0.1` /
/// `[::1]` on any port, or the exact current ngrok host. Wildcards are
/// intentionally rejected — a credentialed API must never echo `*`.
struct CORSPolicy {

    /// Exact-host ngrok reference (read on the NIO thread for CORS).
    let ngrokHostRef: NgrokHostRef

    /// Build a safe CORS origin value. Reflects the `Origin` header only when
    /// it belongs to a trusted host; returns `nil` otherwise.
    func allowedOrigin(from head: HTTPRequestHead) -> String? {
        guard let origin = head.headers["Origin"].first,
              let url = URL(string: origin),
              let host = url.host else {
            return nil
        }
        let allowedHosts: Set<String> = ["localhost", "127.0.0.1", "[::1]"]
        if allowedHosts.contains(host) { return origin }
        if let ngrokHost = ngrokHostRef.host, host == ngrokHost {
            return origin
        }
        return nil
    }
}

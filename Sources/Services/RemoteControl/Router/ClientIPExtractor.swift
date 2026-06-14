// MARK: - ClientIPExtractor
// Resolves the client IP for rate limiting and token binding.
// macOS 14+, Swift 5.10

import Foundation
import NIOCore

/// Resolves the client IP used for rate limiting and token-IP binding.
///
/// SECURITY: We deliberately ignore `X-Forwarded-For` and similar headers.
/// They are trivially spoofable by any client and must never be trusted when
/// the value is used as a security boundary (auth lockout counters, token
/// validation). The only trusted source is the TCP peer address captured by
/// NIO at `channelActive`.
struct ClientIPExtractor: Sendable {

    /// Extract the remote address from a NIO `Channel`.
    /// Returns `"unknown"` when the address is unavailable or non-IP (e.g. UDS).
    func clientIP(from channel: Channel) -> String {
        guard let remoteAddr = channel.remoteAddress else { return "unknown" }
        switch remoteAddr {
        case .v4(let addr):
            return Self.normalizeLoopback(addr.host)
        case .v6(let addr):
            return Self.normalizeLoopback(addr.host)
        default:
            return "unknown"
        }
    }

    /// Loopback string literals that all denote the same local peer.
    ///
    /// A tunnel (ngrok) or local proxy connects to the server over `localhost`,
    /// which resolves to BOTH `127.0.0.1` (IPv4) and `::1` (IPv6). The OS
    /// happy-eyeballs resolver may pick either family per connection, and IPv6
    /// can also surface as the IPv4-mapped form `::ffff:127.0.0.1`. Without
    /// normalization, two connections from the SAME local peer can present
    /// different IP strings — which breaks token-IP binding and forces a
    /// spurious PIN re-prompt. Collapse every loopback spelling to one value.
    static let canonicalLoopback = "127.0.0.1"

    /// Map any loopback spelling to `canonicalLoopback`; pass other IPs through.
    static func normalizeLoopback(_ host: String) -> String {
        switch host.lowercased() {
        case "127.0.0.1", "::1", "::ffff:127.0.0.1", "0:0:0:0:0:0:0:1":
            return canonicalLoopback
        default:
            return host
        }
    }

    /// Whether `host` denotes the local loopback peer (post-normalization).
    static func isLoopback(_ host: String) -> Bool {
        normalizeLoopback(host) == canonicalLoopback
    }
}

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
            return addr.host
        case .v6(let addr):
            return addr.host
        default:
            return "unknown"
        }
    }
}

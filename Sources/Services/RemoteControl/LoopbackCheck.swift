// MARK: - LoopbackCheck
// Single source of truth for loopback-address classification used across the
// Remote Control server (WS upgrade path SEC-H2, debug endpoints SEC-1).
// macOS 14+, Swift 5.10

import Foundation

/// Classifies a TCP-level client IP as a loopback address.
///
/// Consolidates the previously-duplicated `isLoopback` helpers from
/// ``WebSocketUpgradeHandler`` and `DebugEndpointRouter` so the classification
/// (IPv4 `127.0.0.0/8`, IPv6 `::1` canonical forms, IPv4-mapped IPv6 loopback)
/// stays identical everywhere it is enforced.
///
/// The input must be a real TCP-level peer address captured from the channel —
/// never a spoofable header such as `X-Forwarded-For`.
enum LoopbackCheck {

    /// `true` when `ip` is a loopback address.
    static func isLoopback(_ ip: String) -> Bool {
        // IPv4 loopback is the entire 127.0.0.0/8 block.
        if isIPv4Loopback(ip) { return true }
        // IPv6 loopback canonical forms.
        if ip == "::1" || ip == "0:0:0:0:0:0:0:1" { return true }
        // IPv4-mapped IPv6 loopback ("::ffff:127.0.0.1").
        let lower = ip.lowercased()
        if lower.hasPrefix("::ffff:"), isIPv4Loopback(String(lower.dropFirst("::ffff:".count))) {
            return true
        }
        return false
    }

    /// `true` when `s` is a syntactically valid dotted-quad IPv4 address whose
    /// first octet is `127` (the 127.0.0.0/8 loopback block).
    ///
    /// Defense-in-depth: a bare `hasPrefix("127.")` test would misclassify a
    /// spoofed hostname such as `"127.evil.com"` as loopback. Even though the
    /// input is always a real TCP peer address, we require every octet to be a
    /// numeric value in `0...255` so only genuine 127.0.0.0/8 addresses pass.
    private static func isIPv4Loopback(_ s: String) -> Bool {
        let octets = s.split(separator: ".", omittingEmptySubsequences: false)
        guard octets.count == 4 else { return false }
        var values: [Int] = []
        values.reserveCapacity(4)
        for octet in octets {
            guard !octet.isEmpty,
                  octet.allSatisfy({ $0.isASCII && $0.isNumber }),
                  let value = Int(octet), value >= 0, value <= 255 else {
                return false
            }
            values.append(value)
        }
        return values[0] == 127
    }
}

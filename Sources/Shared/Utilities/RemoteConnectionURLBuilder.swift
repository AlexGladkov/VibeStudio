// MARK: - RemoteConnectionURLBuilder
// Builds the auto-login Remote Control connection URL used by QR codes.
// macOS 14+, Swift 5.10

import Foundation

/// Builds the Remote Control connection URL that the iPhone scans from a QR
/// code.
///
/// The URL embeds the current 4-digit PIN so the device can auto-authenticate
/// on first connection. Two transport modes:
/// - **ngrok tunnel** (preferred when active) — routes through an HTTPS
///   reverse-proxy that bypasses LAN restrictions and self-signed certs.
/// - **LAN HTTP** (fallback) — connects directly to the Mac's LAN IPv4 over
///   plain HTTP on port = main port + 1. Plain HTTP avoids iOS Safari
///   refusing self-signed TLS certs for WSS connections (cf. SEC-H2).
enum RemoteConnectionURLBuilder {

    /// Build a connection URL with embedded PIN.
    ///
    /// - Parameters:
    ///   - pin: Current PIN from `RemoteControlServer.currentPin`.
    ///   - ngrokTunnelURL: Active ngrok tunnel URL, or `nil` to use LAN.
    ///   - lanPort: Main server port (HTTP port = lanPort + 1).
    /// - Returns: A user-facing URL string ready to encode as a QR.
    static func build(pin: String, ngrokTunnelURL: String?, lanPort: Int) -> String {
        if let ngrok = ngrokTunnelURL, !ngrok.isEmpty {
            // SEC-H2: defence-in-depth. ngrok already returns an https URL,
            // but if a future change ever serves an http:// tunnel we MUST
            // upgrade it to https here — the server now refuses non-loopback
            // WS upgrades that arrive without an HTTPS hop in front
            // (HTTPRequestRouter.performWebSocketUpgrade).
            let secured = upgradedToHTTPS(ngrok)
            return "\(secured)/?pin=\(pin)"
        }
        let ipAddress = NetworkUtility.localLANIPAddress() ?? "127.0.0.1"
        let httpPort = lanPort + 1
        return "http://\(ipAddress):\(httpPort)/?pin=\(pin)"
    }

    /// Normalise a tunnel URL to use an HTTPS scheme. Idempotent for already
    /// `https://` URLs; rewrites `http://` and `ws://` to `https://`/`wss://`
    /// equivalents.
    private static func upgradedToHTTPS(_ url: String) -> String {
        if url.hasPrefix("https://") || url.hasPrefix("wss://") { return url }
        if url.hasPrefix("http://") {
            return "https://" + url.dropFirst("http://".count)
        }
        if url.hasPrefix("ws://") {
            return "wss://" + url.dropFirst("ws://".count)
        }
        return url
    }
}

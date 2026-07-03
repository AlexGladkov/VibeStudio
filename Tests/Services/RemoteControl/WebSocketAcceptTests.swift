import XCTest
@testable import VibeStudio

/// Unit test for ``WebSocketUpgradeHandler.computeWebSocketAccept(key:)`` against
/// the canonical RFC 6455 §1.3 handshake vector. Pure SHA-1 + base64 — no IO.
final class WebSocketAcceptTests: XCTestCase {

    /// RFC 6455 §1.3: `Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==` must yield
    /// `Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=`.
    func testCanonicalRFC6455Vector() {
        let accept = WebSocketUpgradeHandler.computeWebSocketAccept(key: "dGhlIHNhbXBsZSBub25jZQ==")
        XCTAssertEqual(accept, "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=")
    }

    /// Deterministic: same key → same accept value.
    func testIsDeterministic() {
        let key = "x3JJHMbDL1EzLkh9GBhXDw=="
        XCTAssertEqual(
            WebSocketUpgradeHandler.computeWebSocketAccept(key: key),
            WebSocketUpgradeHandler.computeWebSocketAccept(key: key)
        )
    }
}

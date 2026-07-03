import XCTest
import NIOHTTP1
@testable import VibeStudio

/// Unit tests for the SEC-H2 transport-security seams extracted from
/// ``WebSocketUpgradeHandler.validateTransportSecurity``:
/// ``resolveForwardedProto`` (pure header parse) and ``isTransportTrusted``
/// (loopback OR forwarded-https predicate). No channel / IO involved.
final class TransportSecurityTests: XCTestCase {

    // MARK: - Helpers

    private func makeHead(headers: [(String, String)] = []) -> HTTPRequestHead {
        HTTPRequestHead(
            version: .http1_1,
            method: .GET,
            uri: "/api/v1/terminal/\(UUID().uuidString)",
            headers: HTTPHeaders(headers)
        )
    }

    // MARK: - resolveForwardedProto

    func testResolveXForwardedProtoHTTPS() {
        let head = makeHead(headers: [("X-Forwarded-Proto", "https")])
        XCTAssertEqual(WebSocketUpgradeHandler.resolveForwardedProto(head), "https")
    }

    func testResolveXForwardedProtoHTTP() {
        let head = makeHead(headers: [("X-Forwarded-Proto", "http")])
        XCTAssertEqual(WebSocketUpgradeHandler.resolveForwardedProto(head), "http")
    }

    func testResolveXForwardedProtoIsLowercased() {
        let head = makeHead(headers: [("X-Forwarded-Proto", "HTTPS")])
        XCTAssertEqual(WebSocketUpgradeHandler.resolveForwardedProto(head), "https")
    }

    func testResolveRFC7239ForwardedProto() {
        let head = makeHead(headers: [("Forwarded", "proto=\"https\"")])
        XCTAssertEqual(WebSocketUpgradeHandler.resolveForwardedProto(head), "https")
    }

    func testResolveRFC7239ForwardedProtoCompound() {
        // `Forwarded: for=1.2.3.4; proto="https"` — proto is the second element.
        let head = makeHead(headers: [("Forwarded", "for=1.2.3.4; proto=\"https\"")])
        XCTAssertEqual(WebSocketUpgradeHandler.resolveForwardedProto(head), "https")
    }

    func testResolveNoHeadersReturnsNil() {
        XCTAssertNil(WebSocketUpgradeHandler.resolveForwardedProto(makeHead()))
    }

    func testXForwardedProtoPreferredOverForwarded() {
        let head = makeHead(headers: [
            ("X-Forwarded-Proto", "http"),
            ("Forwarded", "proto=\"https\"")
        ])
        XCTAssertEqual(WebSocketUpgradeHandler.resolveForwardedProto(head), "http")
    }

    // MARK: - isTransportTrusted

    func testLoopbackWithoutHeadersIsTrusted() {
        let trusted = WebSocketUpgradeHandler.isTransportTrusted(
            clientIP: "127.0.0.1", head: makeHead()
        )
        XCTAssertTrue(trusted)
    }

    func testNonLoopbackWithHTTPSForwardedIsTrusted() {
        let head = makeHead(headers: [("X-Forwarded-Proto", "https")])
        XCTAssertTrue(WebSocketUpgradeHandler.isTransportTrusted(clientIP: "10.0.0.1", head: head))
    }

    func testNonLoopbackWithHTTPForwardedIsRejected() {
        let head = makeHead(headers: [("X-Forwarded-Proto", "http")])
        XCTAssertFalse(WebSocketUpgradeHandler.isTransportTrusted(clientIP: "10.0.0.1", head: head))
    }

    func testNonLoopbackWithRFC7239HTTPSIsTrusted() {
        let head = makeHead(headers: [("Forwarded", "proto=\"https\"")])
        XCTAssertTrue(WebSocketUpgradeHandler.isTransportTrusted(clientIP: "203.0.113.7", head: head))
    }

    func testNonLoopbackWithoutHeadersIsRejected() {
        XCTAssertFalse(
            WebSocketUpgradeHandler.isTransportTrusted(clientIP: "10.0.0.1", head: makeHead())
        )
    }

    func testNonLoopbackWithUppercaseHTTPSIsTrusted() {
        let head = makeHead(headers: [("X-Forwarded-Proto", "HTTPS")])
        XCTAssertTrue(WebSocketUpgradeHandler.isTransportTrusted(clientIP: "10.0.0.1", head: head))
    }
}

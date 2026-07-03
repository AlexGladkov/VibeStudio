import XCTest
import NIOHTTP1
@testable import VibeStudio

/// Unit tests for ``CORSPolicy.allowedOrigin`` — the "reflect the Origin only
/// when trusted" decision. Covers loopback allow-list, rejection of untrusted
/// origins, missing/​wildcard origins, and the exact-host ngrok branch (using a
/// real ``NgrokHostRef`` — no fake needed).
final class CORSPolicyTests: XCTestCase {

    // MARK: - Helpers

    private func makeHead(origin: String?) -> HTTPRequestHead {
        var headers = HTTPHeaders()
        if let origin { headers.add(name: "Origin", value: origin) }
        return HTTPRequestHead(version: .http1_1, method: .GET, uri: "/", headers: headers)
    }

    private func policy(ngrokHost: String? = nil) -> CORSPolicy {
        let ref = NgrokHostRef()
        ref.set(ngrokHost)
        return CORSPolicy(ngrokHostRef: ref)
    }

    // MARK: - Loopback allow-list

    func testLocalhostOriginIsReflected() {
        let origin = "http://localhost:3000"
        let result = policy().allowedOrigin(from: makeHead(origin: origin))
        XCTAssertEqual(result, origin)
    }

    func testLoopbackIPv4OriginIsReflected() {
        let origin = "http://127.0.0.1:8080"
        let result = policy().allowedOrigin(from: makeHead(origin: origin))
        XCTAssertEqual(result, origin)
    }

    // MARK: - Rejection

    func testUntrustedOriginReturnsNil() {
        let result = policy().allowedOrigin(from: makeHead(origin: "http://evil.com"))
        XCTAssertNil(result)
    }

    func testMissingOriginReturnsNil() {
        let result = policy().allowedOrigin(from: makeHead(origin: nil))
        XCTAssertNil(result)
    }

    func testWildcardOriginReturnsNil() {
        // A credentialed API must never echo "*". URL(string:"*") has no host,
        // so it is rejected.
        let result = policy().allowedOrigin(from: makeHead(origin: "*"))
        XCTAssertNil(result)
    }

    // MARK: - ngrok exact-host branch

    func testExactNgrokHostIsReflected() {
        let origin = "https://abcd-1234.ngrok-free.app"
        let result = policy(ngrokHost: "abcd-1234.ngrok-free.app")
            .allowedOrigin(from: makeHead(origin: origin))
        XCTAssertEqual(result, origin)
    }

    func testDifferentNgrokHostReturnsNil() {
        let result = policy(ngrokHost: "abcd-1234.ngrok-free.app")
            .allowedOrigin(from: makeHead(origin: "https://other-5678.ngrok-free.app"))
        XCTAssertNil(result)
    }

    func testNgrokOriginRejectedWhenNoTunnelActive() {
        // No ngrok host set → the ngrok branch must not match anything.
        let result = policy(ngrokHost: nil)
            .allowedOrigin(from: makeHead(origin: "https://abcd-1234.ngrok-free.app"))
        XCTAssertNil(result)
    }
}

import XCTest
import NIOHTTP1
@testable import VibeStudio

/// Unit tests for ``RemoteAuthMiddleware`` — bearer-token extraction and the
/// exhaustive ``AuthError`` → HTTP response mapping. Pure static helpers, no
/// per-request state.
final class RemoteAuthMiddlewareTests: XCTestCase {

    // MARK: - Helpers

    private func makeHead(headers: [(String, String)] = []) -> HTTPRequestHead {
        HTTPRequestHead(
            version: .http1_1,
            method: .GET,
            uri: "/api/v1/anything",
            headers: HTTPHeaders(headers)
        )
    }

    // MARK: - extractBearerToken

    func testExtractBearerTokenHappyPath() {
        let head = makeHead(headers: [("Authorization", "Bearer xyz")])
        XCTAssertEqual(RemoteAuthMiddleware.extractBearerToken(from: head), "xyz")
    }

    func testExtractBearerTokenMissingHeaderReturnsNil() {
        let head = makeHead()
        XCTAssertNil(RemoteAuthMiddleware.extractBearerToken(from: head))
    }

    func testExtractBearerTokenBasicSchemeReturnsNil() {
        let head = makeHead(headers: [("Authorization", "Basic dXNlcjpwYXNz")])
        XCTAssertNil(RemoteAuthMiddleware.extractBearerToken(from: head))
    }

    func testExtractBearerTokenEmptyTokenReturnsEmptyString() {
        // "Bearer " with nothing after the space → empty token (not nil).
        let head = makeHead(headers: [("Authorization", "Bearer ")])
        XCTAssertEqual(RemoteAuthMiddleware.extractBearerToken(from: head), "")
    }

    // MARK: - authErrorResponse (exhaustive)

    func testInvalidPinMapping() {
        let r = RemoteAuthMiddleware.authErrorResponse(.invalidPin)
        XCTAssertEqual(r.status, .unauthorized)
        XCTAssertEqual(r.code, "AUTH_PIN_INVALID")
        XCTAssertNil(r.retryAfterSeconds)
    }

    func testRateLimitedMapping() {
        let r = RemoteAuthMiddleware.authErrorResponse(.rateLimited(retryAfterSeconds: 42))
        XCTAssertEqual(r.status, .tooManyRequests)
        XCTAssertEqual(r.code, "AUTH_LOCKOUT")
        XCTAssertEqual(r.retryAfterSeconds, 42)
    }

    func testGlobalLockoutMapping() {
        let r = RemoteAuthMiddleware.authErrorResponse(.globalLockout)
        XCTAssertEqual(r.status, .tooManyRequests)
        XCTAssertEqual(r.code, "AUTH_LOCKOUT")
        // Derived from the single-source-of-truth rate-limit window.
        XCTAssertEqual(r.retryAfterSeconds, Int(RemoteAuthService.rateLimitWindowSecondsPublic))
    }

    func testInvalidTokenMapping() {
        let r = RemoteAuthMiddleware.authErrorResponse(.invalidToken)
        XCTAssertEqual(r.status, .unauthorized)
        XCTAssertEqual(r.code, "AUTH_TOKEN_INVALID")
        XCTAssertNil(r.retryAfterSeconds)
    }

    func testTokenExpiredMapping() {
        let r = RemoteAuthMiddleware.authErrorResponse(.tokenExpired)
        XCTAssertEqual(r.status, .unauthorized)
        XCTAssertEqual(r.code, "AUTH_TOKEN_EXPIRED")
        XCTAssertNil(r.retryAfterSeconds)
    }

    func testIPMismatchMapping() {
        let r = RemoteAuthMiddleware.authErrorResponse(.ipMismatch)
        XCTAssertEqual(r.status, .forbidden)
        XCTAssertEqual(r.code, "AUTH_IP_MISMATCH")
        XCTAssertNil(r.retryAfterSeconds)
    }

    func testMaxDevicesReachedMapping() {
        let r = RemoteAuthMiddleware.authErrorResponse(.maxDevicesReached)
        XCTAssertEqual(r.status, .serviceUnavailable)
        XCTAssertEqual(r.code, "MAX_DEVICES_REACHED")
        XCTAssertNil(r.retryAfterSeconds)
    }

    /// Only the two lockout errors carry a `Retry-After` hint.
    func testRetryAfterPresentOnlyForLockoutErrors() {
        let withRetry: [AuthError] = [.rateLimited(retryAfterSeconds: 10), .globalLockout]
        let withoutRetry: [AuthError] = [
            .invalidPin, .invalidToken, .tokenExpired, .ipMismatch, .maxDevicesReached
        ]
        for error in withRetry {
            XCTAssertNotNil(
                RemoteAuthMiddleware.authErrorResponse(error).retryAfterSeconds,
                "expected retryAfter for \(error)"
            )
        }
        for error in withoutRetry {
            XCTAssertNil(
                RemoteAuthMiddleware.authErrorResponse(error).retryAfterSeconds,
                "unexpected retryAfter for \(error)"
            )
        }
    }
}

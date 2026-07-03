import XCTest
@testable import VibeStudio

/// Security-focused tests for ``RemoteAuthService``: PIN one-time-use, per-IP and
/// global rate limiting, token IP-binding and TTL expiry, device caps, and
/// User-Agent → display-name mapping.
///
/// All time-based behavior is made deterministic by injecting a controllable
/// clock via `RemoteAuthService(now:)`.
@MainActor
final class RemoteAuthServiceTests: XCTestCase {

    // MARK: - Test Doubles

    /// A mutable clock captured by the injected `now` closure.
    private final class Clock {
        var current: Date
        init(_ date: Date) { current = date }
    }

    // MARK: - Helpers

    /// A 6-digit PIN guaranteed to differ from `pin`.
    private func wrongPin(notEqualTo pin: String) -> String {
        pin == "000000" ? "111111" : "000000"
    }

    /// Extract the error from a failed `Result`, failing the test on success.
    private func failure<S>(
        _ result: Result<S, AuthError>,
        file: StaticString = #filePath,
        line: UInt = #line
    ) -> AuthError? {
        switch result {
        case .success:
            XCTFail("expected failure, got success", file: file, line: line)
            return nil
        case .failure(let error):
            return error
        }
    }

    // MARK: - PIN Rate Limiting (per-IP)

    func testWrongPinThreeTimesLocksOutTheIP() {
        let service = RemoteAuthService()
        let ip = "10.0.0.5"
        let bad = wrongPin(notEqualTo: service.currentPin)

        // Three failures from the same IP: each reports the invalid PIN.
        for attempt in 1...3 {
            let result = service.validatePin(bad, clientIP: ip, userAgent: "ua")
            XCTAssertEqual(failure(result), .invalidPin, "attempt \(attempt)")
        }

        // The IP is now locked out — the next attempt is rate-limited.
        let fourth = service.validatePin(bad, clientIP: ip, userAgent: "ua")
        guard case .rateLimited = failure(fourth) else {
            return XCTFail("expected rateLimited after 3 failures, got \(String(describing: failure(fourth)))")
        }
    }

    // MARK: - Global Lockout

    func testTenFailuresTriggerGlobalLockout() {
        let service = RemoteAuthService()
        var lockoutFired = false
        service.onSecurityLockout = { lockoutFired = true }

        let bad = wrongPin(notEqualTo: service.currentPin)

        // Spread failures across 10 distinct IPs so none hits the per-IP limit
        // (max 3/IP) before the global threshold (10 total) is reached.
        for index in 0..<10 {
            _ = service.validatePin(bad, clientIP: "10.0.0.\(index)", userAgent: "ua")
        }

        XCTAssertTrue(service.isLocked, "server should be globally locked after 10 failures")
        XCTAssertTrue(lockoutFired, "onSecurityLockout callback should fire")
    }

    // MARK: - PIN One-Time Use

    func testCorrectPinSucceedsThenIsConsumed() {
        let service = RemoteAuthService()
        let pin = service.currentPin

        let first = service.validatePin(pin, clientIP: "1.2.3.4", userAgent: "iPhone")
        guard case .success(let response) = first else {
            return XCTFail("expected success for the correct PIN")
        }
        XCTAssertFalse(response.token.isEmpty)
        XCTAssertEqual(service.connectedDevices.count, 1)

        // PIN was regenerated on success: replaying the same PIN now fails.
        let replay = service.validatePin(pin, clientIP: "1.2.3.4", userAgent: "iPhone")
        XCTAssertEqual(failure(replay), .invalidPin)
    }

    // MARK: - Device Cap

    func testFourthDeviceExceedsMaxDevices() {
        let service = RemoteAuthService()

        for index in 0..<RemoteAuthService.maxDevices {
            let pin = service.currentPin
            guard case .success = service.validatePin(pin, clientIP: "1.2.3.\(index)", userAgent: "ua") else {
                return XCTFail("device \(index) should authenticate")
            }
        }
        XCTAssertEqual(service.connectedDevices.count, RemoteAuthService.maxDevices)

        // Even with a valid PIN, the device cap is enforced first.
        let pin = service.currentPin
        let overflow = service.validatePin(pin, clientIP: "1.2.3.99", userAgent: "ua")
        XCTAssertEqual(failure(overflow), .maxDevicesReached)
    }

    // MARK: - Token IP Binding

    func testTokenRejectedFromDifferentIP() {
        let service = RemoteAuthService()
        let pin = service.currentPin

        guard case .success(let response) = service.validatePin(pin, clientIP: "1.2.3.4", userAgent: "ua") else {
            return XCTFail("authentication should succeed")
        }

        let mismatched = service.validateToken(response.token, clientIP: "9.9.9.9")
        XCTAssertEqual(failure(mismatched), .ipMismatch)
    }

    // MARK: - Rate-Limit Window Reset (deterministic clock)

    func testRateLimitWindowResetsAfterExpiry() {
        let clock = Clock(Date(timeIntervalSince1970: 1_000_000))
        let service = RemoteAuthService(now: { clock.current })
        let ip = "10.0.0.7"
        let bad = wrongPin(notEqualTo: service.currentPin)

        for _ in 0..<3 {
            _ = service.validatePin(bad, clientIP: ip, userAgent: "ua")
        }

        // Immediately after 3 failures the IP is rate-limited.
        guard case .rateLimited = failure(service.validatePin(bad, clientIP: ip, userAgent: "ua")) else {
            return XCTFail("expected rateLimited within the window")
        }

        // Slide past the 5-minute window (300s): stale failures are pruned.
        clock.current = clock.current.addingTimeInterval(RemoteAuthService.rateLimitWindowSecondsPublic + 1)

        let afterReset = service.validatePin(bad, clientIP: ip, userAgent: "ua")
        XCTAssertEqual(failure(afterReset), .invalidPin, "window should have reset — not rate-limited")
    }

    // MARK: - Token TTL Expiry (deterministic clock)

    func testTokenExpiresAfterTTL() {
        let clock = Clock(Date(timeIntervalSince1970: 2_000_000))
        let service = RemoteAuthService(now: { clock.current })
        let pin = service.currentPin

        guard case .success(let response) = service.validatePin(pin, clientIP: "1.2.3.4", userAgent: "ua") else {
            return XCTFail("authentication should succeed")
        }

        // Just before expiry the token is still valid.
        clock.current = clock.current.addingTimeInterval(RemoteAuthService.tokenTTL - 1)
        guard case .success = service.validateToken(response.token, clientIP: "1.2.3.4") else {
            return XCTFail("token should still be valid one second before TTL")
        }

        // One second past the TTL the token is expired.
        clock.current = clock.current.addingTimeInterval(2)
        XCTAssertEqual(failure(service.validateToken(response.token, clientIP: "1.2.3.4")), .tokenExpired)
    }

    // MARK: - Device Display Name from User-Agent

    func testDisplayNameParsedFromUserAgent() {
        func displayName(forUA userAgent: String) -> String {
            let service = RemoteAuthService()
            guard case .success(let response) = service.validatePin(
                service.currentPin,
                clientIP: "1.1.1.1",
                userAgent: userAgent
            ) else {
                XCTFail("authentication should succeed for UA: \(userAgent)")
                return ""
            }
            return response.device.displayName
        }

        XCTAssertEqual(displayName(forUA: "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X)"), "iPhone")
        XCTAssertEqual(displayName(forUA: "Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X)"), "iPad")
        XCTAssertEqual(displayName(forUA: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)"), "Mac")
        XCTAssertEqual(displayName(forUA: "totally-unknown-agent/1.0"), "Remote Device")
    }
}

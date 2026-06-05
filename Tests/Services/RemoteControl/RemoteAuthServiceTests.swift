// MARK: - RemoteAuthServiceTests
// Security-critical: PIN/token issue, validation, replay, expiry, rate limit, lockout.

import XCTest
@testable import VibeStudio

// MARK: - Result helpers (AuthTokenResponse/RemoteDevice are not Equatable; compare via shape)

private func assertAuthFailure<T>(_ result: Result<T, AuthError>, equals expected: AuthError, file: StaticString = #file, line: UInt = #line) {
    switch result {
    case .success: XCTFail("expected failure(\(expected)), got success", file: file, line: line)
    case .failure(let e): XCTAssertEqual(e, expected, file: file, line: line)
    }
}

@MainActor
final class RemoteAuthServiceTests: XCTestCase {

    // MARK: - PIN Generation

    func test_init_generatesSixDigitPin() {
        let svc = RemoteAuthService()
        XCTAssertEqual(svc.currentPin.count, 6)
        XCTAssertTrue(svc.currentPin.allSatisfy(\.isNumber))
    }

    func test_regeneratePin_producesDifferentPin() {
        // With a 6-digit numeric space, collisions are 1-in-1M; safe in practice.
        let svc = RemoteAuthService()
        var seen: Set<String> = [svc.currentPin]
        for _ in 0..<10 {
            svc.regeneratePin()
            seen.insert(svc.currentPin)
        }
        XCTAssertGreaterThan(seen.count, 1, "regeneratePin must not be deterministic")
    }

    // MARK: - validatePin happy path

    func test_validatePin_correctPin_issuesTokenAndAddsDevice() {
        let svc = RemoteAuthService()
        let pin = svc.currentPin

        let result = svc.validatePin(pin, clientIP: "10.0.0.1", userAgent: "Mozilla/5.0 (iPhone)")

        guard case let .success(resp) = result else {
            return XCTFail("expected success, got \(result)")
        }
        XCTAssertFalse(resp.token.isEmpty)
        XCTAssertEqual(resp.device.ipAddress, "10.0.0.1")
        XCTAssertEqual(resp.device.displayName, "iPhone")
        XCTAssertEqual(svc.connectedDevices.count, 1)
    }

    func test_validatePin_correctPin_consumesPin() {
        let svc = RemoteAuthService()
        let pin = svc.currentPin
        _ = svc.validatePin(pin, clientIP: "10.0.0.1", userAgent: "")
        // Second attempt with the consumed PIN must fail.
        let second = svc.validatePin(pin, clientIP: "10.0.0.2", userAgent: "")
        if case .success = second {
            XCTFail("PIN must be single-use; replay must fail")
        }
    }

    // MARK: - validatePin failures

    func test_validatePin_wrongPin_failsWithInvalidPin() {
        let svc = RemoteAuthService()
        let result = svc.validatePin("000000", clientIP: "10.0.0.1", userAgent: "")
        // Worst case the random pin equals "000000" — try one more value to be safe.
        if case .success = result {
            let alt = svc.validatePin("999999", clientIP: "10.0.0.1", userAgent: "")
            assertAuthFailure(alt, equals: .invalidPin)
            return
        }
        assertAuthFailure(result, equals: .invalidPin)
    }

    func test_validatePin_perIPRateLimit_lockoutAfterThreeFailures() {
        let svc = RemoteAuthService()
        for _ in 0..<3 {
            _ = svc.validatePin("nope-\(UUID().uuidString)", clientIP: "10.0.0.99", userAgent: "")
        }
        let r = svc.validatePin("nope-final", clientIP: "10.0.0.99", userAgent: "")
        guard case let .failure(err) = r, case .rateLimited(let retry) = err else {
            return XCTFail("expected rateLimited, got \(r)")
        }
        XCTAssertGreaterThan(retry, 0)
    }

    func test_validatePin_rateLimitIsPerIP() {
        let svc = RemoteAuthService()
        // Exhaust the IP-A limit.
        for _ in 0..<3 {
            _ = svc.validatePin("wrong", clientIP: "IP_A", userAgent: "")
        }
        // IP-B should not be affected.
        let r = svc.validatePin("also-wrong", clientIP: "IP_B", userAgent: "")
        assertAuthFailure(r, equals: .invalidPin)
    }

    func test_validatePin_globalLockout_after10Failures_setsIsLockedAndCallsCallback() {
        let svc = RemoteAuthService()
        let exp = expectation(description: "onSecurityLockout")
        svc.onSecurityLockout = { exp.fulfill() }

        // 10 failures spread across IPs to avoid hitting per-IP cap first.
        for i in 0..<12 {
            _ = svc.validatePin("wrong", clientIP: "ip_\(i)", userAgent: "")
        }
        wait(for: [exp], timeout: 1.0)
        XCTAssertTrue(svc.isLocked)

        // Once locked, even a correct PIN must be rejected.
        let r = svc.validatePin(svc.currentPin, clientIP: "fresh-ip", userAgent: "")
        assertAuthFailure(r, equals: .globalLockout)
    }

    func test_resetLockout_clearsState() {
        let svc = RemoteAuthService()
        for i in 0..<12 {
            _ = svc.validatePin("wrong", clientIP: "ip_\(i)", userAgent: "")
        }
        XCTAssertTrue(svc.isLocked)
        svc.resetLockout()
        XCTAssertFalse(svc.isLocked)
        // After reset, the PIN has been regenerated; using the new one must work.
        let r = svc.validatePin(svc.currentPin, clientIP: "fresh", userAgent: "")
        if case .failure(let e) = r {
            XCTFail("expected success after reset, got \(e)")
        }
    }

    func test_validatePin_maxDevicesReached() {
        let svc = RemoteAuthService()
        for i in 0..<RemoteAuthService.maxDevices {
            let r = svc.validatePin(svc.currentPin, clientIP: "ip_\(i)", userAgent: "")
            if case .failure(let e) = r {
                XCTFail("setup failure: \(e)")
            }
        }
        let r = svc.validatePin(svc.currentPin, clientIP: "ip_overflow", userAgent: "")
        assertAuthFailure(r, equals: .maxDevicesReached)
    }

    // MARK: - Token validation

    func test_validateToken_returnsDevice_whenIPMatches() {
        let svc = RemoteAuthService()
        guard case let .success(resp) = svc.validatePin(svc.currentPin, clientIP: "1.2.3.4", userAgent: "Macintosh") else {
            return XCTFail()
        }
        let r = svc.validateToken(resp.token, clientIP: "1.2.3.4")
        guard case let .success(dev) = r else {
            return XCTFail("expected device, got \(r)")
        }
        XCTAssertEqual(dev.id, resp.device.id)
        XCTAssertEqual(dev.displayName, "Mac")
    }

    func test_validateToken_ipMismatch_returnsError() {
        let svc = RemoteAuthService()
        guard case let .success(resp) = svc.validatePin(svc.currentPin, clientIP: "1.2.3.4", userAgent: "") else {
            return XCTFail()
        }
        let r = svc.validateToken(resp.token, clientIP: "9.9.9.9")
        assertAuthFailure(r, equals: .ipMismatch)
    }

    func test_validateToken_unknownToken_returnsInvalid() {
        let svc = RemoteAuthService()
        let r = svc.validateToken("does-not-exist", clientIP: "1.2.3.4")
        assertAuthFailure(r, equals: .invalidToken)
    }

    // MARK: - Revocation

    func test_revokeDevice_removesTokenAndDevice() {
        let svc = RemoteAuthService()
        guard case let .success(resp) = svc.validatePin(svc.currentPin, clientIP: "1.2.3.4", userAgent: "") else {
            return XCTFail()
        }
        svc.revokeDevice(resp.device.id)
        XCTAssertTrue(svc.connectedDevices.isEmpty)
        let r = svc.validateToken(resp.token, clientIP: "1.2.3.4")
        assertAuthFailure(r, equals: .invalidToken)
    }

    func test_revokeAllDevices_clearsEverything() {
        let svc = RemoteAuthService()
        _ = svc.validatePin(svc.currentPin, clientIP: "1", userAgent: "")
        _ = svc.validatePin(svc.currentPin, clientIP: "2", userAgent: "")
        svc.revokeAllDevices()
        XCTAssertTrue(svc.connectedDevices.isEmpty)
    }

    // MARK: - Device-changed callback

    func test_validatePin_onDevicesChanged_firesOnAddAndRevoke() {
        let svc = RemoteAuthService()
        var counts: [Int] = []
        svc.onDevicesChanged = { counts.append($0) }
        guard case let .success(resp) = svc.validatePin(svc.currentPin, clientIP: "1", userAgent: "") else {
            return XCTFail()
        }
        svc.revokeDevice(resp.device.id)
        XCTAssertEqual(counts, [1, 0])
    }

    // MARK: - User-Agent parsing

    func test_validatePin_parsesUserAgent_intoDeviceName() {
        let cases: [(String, String)] = [
            ("Mozilla/5.0 (iPhone; CPU iPhone OS 17_5)", "iPhone"),
            ("Mozilla/5.0 (iPad; ...)", "iPad"),
            ("Mozilla/5.0 (Macintosh; ...)", "Mac"),
            ("Mozilla/5.0 (Linux; Android 14)", "Android"),
            ("Mozilla/5.0 (X11; Linux x86_64)", "Linux"),
            ("Mozilla/5.0 (Windows NT 10.0)", "Windows"),
            ("custom-cli/1.0", "Remote Device")
        ]
        for (ua, expected) in cases {
            let svc = RemoteAuthService()
            guard case let .success(r) = svc.validatePin(svc.currentPin, clientIP: "10.0.0.1", userAgent: ua) else {
                XCTFail("auth failed for UA \(ua)"); continue
            }
            XCTAssertEqual(r.device.displayName, expected, "UA: \(ua)")
        }
    }
}

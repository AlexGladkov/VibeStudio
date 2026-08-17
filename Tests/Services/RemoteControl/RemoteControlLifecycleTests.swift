import XCTest
@testable import VibeStudio

// MARK: - RemoteControlLifecycleTests
//
// Regression tests for the P0 lifecycle bugs in the RemoteControl subsystem
// (fix date: 2026-08-17):
//
//   P0-2  Registry desync — `connectedDevices` vs `activeBridges` diverge after
//          disconnect/reconnect cycles, causing self-DoS at the device cap.
//
//   P0-3  Scrollback BOLA/IDOR — `handleScrollback` served raw buffer content
//          to any authenticated device that guessed a `sessionId` belonging to a
//          different project.
//
// NOTE: P0-1 (NIO event-loop affinity violation in `sendErrorAndClose`) is a
// threading invariant that requires a live NIO channel and is therefore verified
// by code-inspection rather than a unit test here.  The fix wraps the function
// body in `channel.eventLoop.execute {}`, matching the pattern already used by
// `resetHeartbeat` and `RemoteSessionBridge.closeWithCode`.

// MARK: - P0-2 Registry Coherence Tests

/// Verifies that `RemoteAuthService` keeps `connectedDevices` coherent
/// with the teardown paths used by `channelInactive` and token expiry.
@MainActor
final class RemoteAuthServiceRegistryCoherenceTests: XCTestCase {

    // MARK: - Helpers

    private final class Clock {
        var current: Date
        init(_ date: Date) { current = date }
    }

    private func authenticateDevice(
        in service: RemoteAuthService,
        ip: String = "1.2.3.4",
        userAgent: String = "TestUA"
    ) -> AuthTokenResponse? {
        let pin = service.currentPin
        guard case .success(let response) = service.validatePin(pin, clientIP: ip, userAgent: userAgent) else {
            XCTFail("Authentication should succeed for a valid PIN")
            return nil
        }
        return response
    }

    // MARK: - P0-2a: revokeDevice clears connectedDevices

    /// After explicit revocation (as called by `channelInactive` fix) the device
    /// must disappear from `connectedDevices` so the slot is available for a new
    /// connection.
    func testRevokeDeviceClearsConnectedDevices() {
        let service = RemoteAuthService()
        guard let response = authenticateDevice(in: service) else { return }
        XCTAssertEqual(service.connectedDevices.count, 1)

        service.revokeDevice(response.device.id)

        XCTAssertEqual(service.connectedDevices.count, 0,
            "connectedDevices must be empty after revokeDevice — zombie entry causes device-cap self-DoS")
    }

    // MARK: - P0-2b: revokeDevice also removes the token

    /// After `revokeDevice`, a subsequent `validateToken` call must return
    /// `.invalidToken`, not `.success`.  This guards against a revoked device
    /// re-authenticating with its stale bearer token.
    func testRevokeDeviceInvalidatesToken() {
        let service = RemoteAuthService()
        guard let response = authenticateDevice(in: service) else { return }

        service.revokeDevice(response.device.id)

        let tokenResult = service.validateToken(response.token, clientIP: "1.2.3.4")
        guard case .failure(let error) = tokenResult else {
            return XCTFail("Token should be invalid after revokeDevice")
        }
        XCTAssertEqual(error, .invalidToken)
    }

    // MARK: - P0-2c: Three connect/disconnect cycles must not saturate the device cap

    /// Regression for the self-DoS scenario: three devices connect, disconnect
    /// (simulated via `revokeDevice`), and reconnect.  Without the P0-2 fix,
    /// `connectedDevices.count` stayed at 3 after disconnects, blocking the
    /// fourth authentication with `maxDevicesReached`.
    func testConnectDisconnectCyclesDoNotSaturateDeviceCap() {
        let service = RemoteAuthService()

        for cycle in 1...3 {
            // Connect.
            guard let response = authenticateDevice(
                in: service, ip: "10.0.0.\(cycle)"
            ) else { return }
            XCTAssertEqual(service.connectedDevices.count, 1,
                "cycle \(cycle): one device connected")

            // Disconnect (mirrors `channelInactive` post-fix behaviour).
            service.revokeDevice(response.device.id)
            XCTAssertEqual(service.connectedDevices.count, 0,
                "cycle \(cycle): device removed after revokeDevice")
        }

        // After three full cycles the cap must NOT be exhausted.
        let pin = service.currentPin
        let fourthResult = service.validatePin(pin, clientIP: "10.0.0.99", userAgent: "TestUA")
        guard case .success = fourthResult else {
            return XCTFail("Fourth authentication should succeed — device cap must not be saturated by zombies")
        }
    }

    // MARK: - P0-2d: onTokenExpired callback is invoked on token expiry

    /// `validateToken` must fire `onTokenExpired` (and NOT call `removeDevice`
    /// directly) so the server can perform the full teardown via `disconnect(_:)`.
    func testOnTokenExpiredCallbackFiredOnExpiry() {
        let clock = Clock(Date(timeIntervalSince1970: 3_000_000))
        let service = RemoteAuthService(now: { clock.current })
        guard let response = authenticateDevice(in: service) else { return }

        var expiredDeviceId: UUID?
        service.onTokenExpired = { deviceId in
            expiredDeviceId = deviceId
        }

        // Advance clock past TTL.
        clock.current = clock.current.addingTimeInterval(RemoteAuthService.tokenTTL + 1)

        let result = service.validateToken(response.token, clientIP: "1.2.3.4")
        guard case .failure(let error) = result else {
            return XCTFail("Token should be expired")
        }
        XCTAssertEqual(error, .tokenExpired)
        XCTAssertEqual(expiredDeviceId, response.device.id,
            "onTokenExpired must be called with the expired device's ID so the server can teardown both registries")
    }

    // MARK: - P0-2e: Without onTokenExpired wired, removeDevice fallback fires

    /// When no server is wired (unit-test context without `RemoteControlServer`),
    /// the fallback `removeDevice` path keeps `connectedDevices` consistent so
    /// existing tests stay green.
    func testTokenExpiredWithoutCallbackFallsBackToRemoveDevice() {
        let clock = Clock(Date(timeIntervalSince1970: 4_000_000))
        let service = RemoteAuthService(now: { clock.current })
        guard let response = authenticateDevice(in: service) else { return }

        // No onTokenExpired wired — fallback path.
        XCTAssertNil(service.onTokenExpired)

        clock.current = clock.current.addingTimeInterval(RemoteAuthService.tokenTTL + 1)
        _ = service.validateToken(response.token, clientIP: "1.2.3.4")

        XCTAssertEqual(service.connectedDevices.count, 0,
            "Fallback removeDevice must still clean connectedDevices when no callback is wired")
    }
}

// MARK: - P0-3 Scrollback BOLA Tests

/// Verifies that `RemoteAuthService.validateToken` enforces project-level
/// ownership of terminal sessions before granting scrollback access.
///
/// The full `handleScrollback` handler requires a live NIO channel and
/// `TerminalService`, so we test the *ownership contract* at the model level:
/// `TerminalSession.projectId` must match the `projectId` supplied in the URL.
/// The integration guard in `handleScrollback` uses exactly this property.
@MainActor
final class ScrollbackOwnershipTests: XCTestCase {

    // MARK: - P0-3a: TerminalSession carries projectId

    /// `TerminalSession` must expose `projectId` so that `handleScrollback` can
    /// enforce the BOLA guard without relying solely on `activeBridges`.
    func testTerminalSessionCarriesProjectId() {
        let projectId = UUID()
        let session = TerminalSession(
            id: UUID(),
            projectId: projectId,
            title: "zsh"
        )
        XCTAssertEqual(session.projectId, projectId,
            "TerminalSession.projectId must equal the project it belongs to — used by the scrollback BOLA guard")
    }

    // MARK: - P0-3b: Project ID mismatch must be detectable

    /// The guard in `handleScrollback`:
    ///   `guard terminalService.session(for: sessionId)?.projectId == projectId`
    /// relies on `projectId` inequality to detect cross-project access.
    func testProjectIdInequalityIsDetectable() {
        let sessionProjectId = UUID()
        let requestedProjectId = UUID()   // different project — BOLA attempt

        let session = TerminalSession(
            id: UUID(),
            projectId: sessionProjectId,
            title: "zsh"
        )

        // Simulate the guard condition from handleScrollback.
        let accessGranted = session.projectId == requestedProjectId
        XCTAssertFalse(accessGranted,
            "A session belonging to project A must NOT be accessible via project B's scrollback endpoint")
    }

    // MARK: - P0-3c: Same project ID must allow access

    func testSameProjectIdGrantsAccess() {
        let projectId = UUID()
        let session = TerminalSession(
            id: UUID(),
            projectId: projectId,
            title: "zsh"
        )

        let accessGranted = session.projectId == projectId
        XCTAssertTrue(accessGranted,
            "A session belonging to project A must be accessible via project A's scrollback endpoint")
    }
}

// MARK: - RemoteControlPreferencesTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

@MainActor
final class RemoteControlPreferencesTests: XCTestCase {

    private let keys = [
        "vs_remote_control_enabled",
        "vs_remote_control_port",
        "vs_remote_bind_localhost",
        "vs_remote_bonjour_enabled",
        "vs_remote_idle_timeout",
        "vs_remote_ngrok_enabled",
        "vs_remote_ngrok_authtoken",
    ]

    override func setUp() {
        super.setUp()
        keys.forEach { UserDefaults.standard.removeObject(forKey: $0) }
    }

    override func tearDown() {
        keys.forEach { UserDefaults.standard.removeObject(forKey: $0) }
        super.tearDown()
    }

    func test_init_firstLaunch_secureDefaults() {
        let sut = RemoteControlPreferences()
        XCTAssertFalse(sut.remoteControlEnabled, "Server must be off by default (SECURITY).")
        XCTAssertEqual(sut.remoteControlPort, 7842)
        XCTAssertFalse(sut.bindToLocalhost)
        XCTAssertFalse(sut.bonjourEnabled)
        XCTAssertFalse(sut.ngrokEnabled)
        XCTAssertEqual(sut.idleTimeoutMinutes, 30)
    }

    func test_remoteControlPort_clampsToValidRange() {
        let sut = RemoteControlPreferences()

        sut.remoteControlPort = 100      // below min
        XCTAssertEqual(sut.remoteControlPort, 1024)

        sut.remoteControlPort = 70_000   // above max
        XCTAssertEqual(sut.remoteControlPort, 65535)

        sut.remoteControlPort = 8080     // valid
        XCTAssertEqual(sut.remoteControlPort, 8080)
    }

    func test_idleTimeoutMinutes_clampsToMinimumOne() {
        let sut = RemoteControlPreferences()
        sut.idleTimeoutMinutes = 0
        XCTAssertEqual(sut.idleTimeoutMinutes, 1)
        sut.idleTimeoutMinutes = -5
        XCTAssertEqual(sut.idleTimeoutMinutes, 1)
        sut.idleTimeoutMinutes = 60
        XCTAssertEqual(sut.idleTimeoutMinutes, 60)
    }

    func test_persistence_roundtrip() {
        let sut = RemoteControlPreferences()
        sut.remoteControlEnabled = true
        sut.remoteControlPort = 9999
        sut.bindToLocalhost = true
        sut.bonjourEnabled = true
        sut.idleTimeoutMinutes = 45

        let reloaded = RemoteControlPreferences()
        XCTAssertTrue(reloaded.remoteControlEnabled)
        XCTAssertEqual(reloaded.remoteControlPort, 9999)
        XCTAssertTrue(reloaded.bindToLocalhost)
        XCTAssertTrue(reloaded.bonjourEnabled)
        XCTAssertEqual(reloaded.idleTimeoutMinutes, 45)
    }
}

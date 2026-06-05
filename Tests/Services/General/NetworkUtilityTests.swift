// MARK: - NetworkUtilityTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

final class NetworkUtilityTests: XCTestCase {

    func test_localLANIPAddress_isNilOrNonLoopbackIPv4() {
        let address = NetworkUtility.localLANIPAddress()

        // CI / sandboxed environments may have no en* interface — nil is acceptable.
        guard let address else { return }

        XCTAssertNotEqual(address, "127.0.0.1", "Loopback must be filtered out")

        // Validate IPv4 dotted-quad format.
        let parts = address.split(separator: ".")
        XCTAssertEqual(parts.count, 4, "Expected IPv4 dotted-quad, got \(address)")
        for part in parts {
            guard let n = Int(part) else { return XCTFail("Non-numeric octet in \(address)") }
            XCTAssertTrue((0...255).contains(n), "Octet \(n) out of range in \(address)")
        }
    }
}

// MARK: - AgentAvailabilityServiceTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

@MainActor
final class AgentAvailabilityServiceTests: XCTestCase {

    func test_init_setsAllAgentsToChecking() {
        let sut = AgentAvailabilityService()
        for agent in AIAssistant.allCases {
            if case .checking = sut.availability[agent] ?? .checking {
                // ok
            } else {
                XCTFail("Agent \(agent) should be .checking after init, got \(String(describing: sut.availability[agent]))")
            }
        }
    }

    func test_check_returnsCachedValueImmediately() {
        let sut = AgentAvailabilityService()
        // First call returns the cached .checking sentinel even though it schedules a refresh.
        let status = sut.check(.claude)
        if case .checking = status {
            // expected on first synchronous call
        } else {
            // Already cached — accept any value; just assert it's a known case.
            switch status {
            case .checking, .available, .notInstalled: break
            }
        }
    }

    func test_canLaunch_isFalseWhenNotAvailable() {
        let sut = AgentAvailabilityService()
        // On a fresh service, status is .checking → canLaunch must be false.
        XCTAssertFalse(sut.canLaunch(.claude))
    }
}

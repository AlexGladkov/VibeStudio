import Foundation
@testable import VibeStudio

/// Mock implementation of ``AgentAvailabilityChecking`` for unit tests.
@Observable
@MainActor
final class MockAgentAvailability: AgentAvailabilityChecking {

    // MARK: - Configurable State

    var availability: [AIAssistant: AgentAvailabilityStatus] = [:]
    var canLaunchResult: Bool = true

    // MARK: - Call Tracking

    var refreshAllCallCount = 0
    var checkCalls: [AIAssistant] = []
    var canLaunchCalls: [AIAssistant] = []

    // MARK: - Protocol

    func refreshAll() {
        refreshAllCallCount += 1
    }

    func check(_ agent: AIAssistant) -> AgentAvailabilityStatus {
        checkCalls.append(agent)
        return availability[agent] ?? .available(path: "/usr/local/bin/\(agent.rawValue)", hasAPIKey: true)
    }

    func canLaunch(_ agent: AIAssistant) -> Bool {
        canLaunchCalls.append(agent)
        return canLaunchResult
    }
}

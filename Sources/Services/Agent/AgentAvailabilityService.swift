// MARK: - AgentAvailabilityService
// Checks and caches availability of AI CLI agents.
// macOS 14+, Swift 5.10

import Foundation
import Observation
import OSLog

/// Concrete implementation of ``AgentAvailabilityChecking``.
///
/// Checks whether each agent's CLI binary exists in trusted directories
/// and whether the required API key is available (Keychain or environment).
/// Results are cached for 30 seconds to avoid filesystem thrashing.
@Observable
@MainActor
final class AgentAvailabilityService: AgentAvailabilityChecking {

    // MARK: - Observable State

    private(set) var availability: [AIAssistant: AgentAvailabilityStatus] = [:]

    // MARK: - Private State

    /// Timestamp of the last full refresh.
    private var lastRefreshDate: Date = .distantPast

    /// Cache TTL in seconds.
    private let cacheTTL: TimeInterval = 30

    // MARK: - Init

    init() {
        // Initialize all agents as .checking.
        for agent in AIAssistant.allCases {
            availability[agent] = .checking
        }
    }

    // MARK: - AgentAvailabilityChecking

    func refreshAll() {
        lastRefreshDate = Date()

        for agent in AIAssistant.allCases {
            let resolvedPath = CLIAgentPathResolver.resolve(agent.executableName)

            if let path = resolvedPath {
                let hasKey = resolveAPIKeyAvailability(for: agent)
                availability[agent] = .available(path: path, hasAPIKey: hasKey)
            } else {
                availability[agent] = .notInstalled(installHint: agent.installHint)
            }
        }

        Logger.services.debug("AgentAvailabilityService: refreshed all agents")
    }

    func check(_ agent: AIAssistant) -> AgentAvailabilityStatus {
        // Refresh if cache is stale.
        if Date().timeIntervalSince(lastRefreshDate) > cacheTTL {
            refreshAll()
        }
        return availability[agent] ?? .checking
    }

    func canLaunch(_ agent: AIAssistant) -> Bool {
        let status = check(agent)
        switch status {
        case .available(_, let hasAPIKey):
            // If the agent doesn't require an API key, it can always launch.
            if agent.apiKeyEnvironmentVariable == nil {
                return true
            }
            return hasAPIKey
        case .notInstalled, .checking:
            return false
        }
    }

    // MARK: - Private

    /// Check whether the agent's API key is available in Keychain or environment.
    private func resolveAPIKeyAvailability(for agent: AIAssistant) -> Bool {
        guard let envVar = agent.apiKeyEnvironmentVariable else {
            // Agent doesn't need an API key.
            return true
        }

        // Check Keychain first.
        if let keychainValue = KeychainHelper.load(account: envVar),
           !keychainValue.isEmpty {
            return true
        }

        // Fallback to environment variable.
        if let envValue = ProcessInfo.processInfo.environment[envVar],
           !envValue.isEmpty {
            return true
        }

        return false
    }
}

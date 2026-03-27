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
        // Stamp immediately to prevent concurrent calls from queueing duplicate Tasks.
        lastRefreshDate = Date()

        // Capture all needed data before leaving MainActor.
        let agents = AIAssistant.allCases
        let installHints = Dictionary(uniqueKeysWithValues: agents.map { ($0, $0.installHint) })
        let executableNames = Dictionary(uniqueKeysWithValues: agents.map { ($0, $0.executableName) })
        let envVars = Dictionary(uniqueKeysWithValues: agents.map { ($0, $0.apiKeyEnvironmentVariable) })

        // Filesystem + Keychain checks block the calling thread.
        // Run them detached so the MainActor queue stays free.
        Task.detached(priority: .userInitiated) { [weak self] in
            var results: [AIAssistant: AgentAvailabilityStatus] = [:]

            for agent in agents {
                guard let name = executableNames[agent] else { continue }
                if let path = CLIAgentPathResolver.resolve(name) {
                    let hasKey = AgentAvailabilityService.resolveAPIKeyAvailability(envVar: envVars[agent] ?? nil)
                    results[agent] = .available(path: path, hasAPIKey: hasKey)
                } else {
                    results[agent] = .notInstalled(installHint: installHints[agent] ?? "")
                }
            }

            // Shadow as `let` to avoid capturing a `var` across actor boundaries.
            let finalResults = results
            await MainActor.run { [weak self] in
                guard let self else { return }
                for (agent, status) in finalResults {
                    self.availability[agent] = status
                }
                Logger.services.debug("AgentAvailabilityService: refreshed all agents")
            }
        }
    }

    func check(_ agent: AIAssistant) -> AgentAvailabilityStatus {
        // Kick off a background refresh if cache is stale.
        // Returns the current cached value immediately (may be .checking on first call).
        if Date().timeIntervalSince(lastRefreshDate) > cacheTTL {
            refreshAll()
        }
        return availability[agent] ?? .checking
    }

    func canLaunch(_ agent: AIAssistant) -> Bool {
        let status = check(agent)
        // An agent can launch whenever its binary is found, regardless of whether
        // an API key is pre-configured.  Agents that require a key (codex, gemini,
        // qwen) handle the missing-key case themselves by prompting the user
        // interactively inside the terminal session.  The "API key not set" badge
        // in the picker is informational only — it does not block execution.
        if case .available = status { return true }
        return false
    }

    // MARK: - Private

    /// Check whether an API key is available in Keychain or environment.
    ///
    /// `nonisolated` + `static` so it can be called from `Task.detached` without
    /// hopping back to `@MainActor`. Both `KeychainHelper.load` and
    /// `ProcessInfo.processInfo.environment` are thread-safe.
    nonisolated private static func resolveAPIKeyAvailability(envVar: String?) -> Bool {
        guard let envVar else {
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

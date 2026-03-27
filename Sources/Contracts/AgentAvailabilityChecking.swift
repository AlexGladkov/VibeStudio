// MARK: - AgentAvailabilityChecking
// Protocol for checking whether AI CLI agents are installed and configured.
// macOS 14+, Swift 5.10

import Foundation

// MARK: - AgentAvailabilityStatus

/// The availability status of an AI CLI agent.
enum AgentAvailabilityStatus: Sendable, Equatable {
    /// Agent binary was found at `path`. `hasAPIKey` indicates whether
    /// the required API key is present (Keychain or environment).
    case available(path: String, hasAPIKey: Bool)
    /// Agent binary was not found. `installHint` is the install command.
    case notInstalled(installHint: String)
    /// Status is being checked (transient state during refresh).
    case checking
}

// MARK: - Protocol

/// Checks and caches the availability of AI CLI agents.
///
/// Implementations must be `@Observable` and `@MainActor` so that
/// SwiftUI views can reactively update the toolbar picker.
@MainActor
protocol AgentAvailabilityChecking: AnyObject, Observable {

    /// Cached availability status for each agent.
    var availability: [AIAssistant: AgentAvailabilityStatus] { get }

    /// Refresh availability for all agents.
    func refreshAll()

    /// Check the current status of a specific agent (uses cache).
    func check(_ agent: AIAssistant) -> AgentAvailabilityStatus

    /// Whether the agent can be launched (installed + API key present or not required).
    func canLaunch(_ agent: AIAssistant) -> Bool
}

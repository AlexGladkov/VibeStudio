// MARK: - AgentVersionChecking
// Protocol for reading installed CLI agent versions and triggering in-place updates.
// macOS 14+, Swift 5.10

import Foundation

// MARK: - AgentVersionState

/// The version state of an AI CLI agent.
enum AgentVersionState: Equatable {
    /// No version check has been performed yet.
    case unknown
    /// A version check is currently in progress.
    case checking
    /// The agent is installed and its version string was successfully parsed.
    case installed(String)
    /// The agent binary was not found in any trusted directory.
    case notInstalled
}

// MARK: - Protocol

/// Reads the installed version of AI CLI agents and performs in-place updates.
///
/// Implementations must be `@Observable` and `@MainActor` so that
/// SwiftUI views can reactively update the settings pane on version checks and
/// update progress without requiring manual refresh.
@MainActor
protocol AgentVersionChecking: AnyObject, Observable {

    /// Cached version state for the given agent.
    func versionState(for agent: AIAssistant) -> AgentVersionState

    /// Whether an update command is currently running for this agent.
    func isUpdating(_ agent: AIAssistant) -> Bool

    /// Last update result message for this agent.
    /// `nil` if no update has been attempted since the last launch.
    func lastMessage(for agent: AIAssistant) -> String?

    /// Run `<binary> --version` in the background and update the cached state.
    func refresh(_ agent: AIAssistant)

    /// Execute the agent's ``AIAssistant/updateCommand`` via `/bin/zsh -lc`
    /// in a background login shell, then refresh the version once complete.
    func update(_ agent: AIAssistant)
}

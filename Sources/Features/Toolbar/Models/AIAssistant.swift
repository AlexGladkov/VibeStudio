// MARK: - AIAssistant
// Domain model for supported AI code assistants.
// macOS 14+, Swift 5.10

import Foundation

/// Supported AI code assistants.
enum AIAssistant: String, CaseIterable, Identifiable, Sendable {
    case claude
    case opencode

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .claude:    return "claude"
        case .opencode:  return "opencode"
        }
    }

    /// Shell command to start this assistant in the terminal.
    var launchCommand: String {
        switch self {
        case .claude:   return "claude --dangerously-skip-permissions\n"
        case .opencode: return "opencode\n"
        }
    }
}

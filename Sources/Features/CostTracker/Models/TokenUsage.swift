// MARK: - TokenUsage
// Value object for parsed token usage from agent CLI output.
// macOS 14+, Swift 5.10

import Foundation

/// Parsed token usage from a single agent output line or JSON block.
///
/// Produced by ``TokenUsageParser`` after stripping ANSI escape sequences
/// and matching one of the recognised CLI output formats (claude text,
/// claude JSON, opencode).
struct TokenUsage: Equatable {
    /// Prompt / input token count.
    let promptTokens: Int
    /// Completion / output token count.
    let completionTokens: Int
    /// Agent model string if parseable from the output line.
    let model: String?
    /// Estimated USD cost if a model price was found; nil if model is unknown.
    let estimatedCostUSD: Double?
}

/// Accumulated cost snapshot for a single agent session.
///
/// Stored in ``CostTrackerService`` keyed by session UUID.
/// Lives outside `TerminalSession` to avoid touching the hot-path
/// updateSessionState / sessionsChanged broadcast.
struct SessionCostSnapshot: Equatable {
    var promptTokens: Int = 0
    var completionTokens: Int = 0
    /// Accumulated estimated USD cost; nil when model is unknown.
    var costUSD: Double?
    /// Last known model name from parsed output.
    var model: String?

    var totalTokens: Int { promptTokens + completionTokens }

    mutating func accumulate(_ usage: TokenUsage) {
        promptTokens += usage.promptTokens
        completionTokens += usage.completionTokens
        model = usage.model ?? model
        if let c = usage.estimatedCostUSD {
            costUSD = (costUSD ?? 0) + c
        }
    }
}

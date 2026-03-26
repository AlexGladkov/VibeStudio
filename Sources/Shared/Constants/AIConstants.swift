// MARK: - AIConstants
// Constants for AI commit message generation.
// macOS 14+, Swift 5.10

import Foundation

/// Constants used by the AI commit message generation feature.
enum AIConstants {
    /// Claude model used for commit message generation.
    static let commitModel = "claude-haiku-4-5-20251001"

    /// Maximum tokens in the model response.
    static let commitMaxTokens = 200

    /// Maximum characters of diff sent to the API.
    static let maxDiffLength = 8_000

    /// Anthropic Messages API endpoint URL.
    static let anthropicAPIURL = "https://api.anthropic.com/v1/messages"

    /// Anthropic API version header value.
    static let anthropicVersion = "2023-06-01"
}

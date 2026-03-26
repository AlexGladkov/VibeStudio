// MARK: - AICommitServicing Protocol
// Контракт для AI-генерации сообщений коммитов.
// macOS 14+, Swift 5.10

import Foundation

// MARK: - Protocol

/// Contract for AI-powered commit message generation.
///
/// Implementations call a remote LLM to produce a concise conventional
/// commit message from a staged git diff.
protocol AICommitServicing: Sendable {
    /// Generate a conventional commit message for the given diff text.
    ///
    /// - Parameter diff: The full staged diff (may be truncated internally).
    /// - Returns: A concise commit message string.
    /// - Throws: ``AICommitServiceError`` when the API key is missing,
    ///   the request fails, or the response cannot be parsed.
    func generateCommitMessage(for diff: String) async throws -> String
}

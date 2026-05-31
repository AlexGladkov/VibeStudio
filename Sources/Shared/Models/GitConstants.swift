// MARK: - GitConstants
// Shared numeric constants for git-related business rules.
// macOS 14+, Swift 5.10

import Foundation

/// Project-wide constants for git-related limits and conventions.
enum GitConstants {

    /// Maximum length (in characters) for a conventional commit message *summary* (first line).
    ///
    /// Lines longer than this are flagged in red in `CommitPanelView`'s
    /// char counter to encourage concise summaries.
    static let commitSummaryMaxLength = 72
}

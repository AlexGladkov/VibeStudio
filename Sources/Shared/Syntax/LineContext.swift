// MARK: - LineContext
// Multiline state carrier for line-by-line syntax parsing.
// macOS 14+, Swift 5.10

import Foundation

/// Carries multiline parsing state from one line to the next.
///
/// Allows parsers to correctly handle constructs spanning multiple lines
/// (YAML frontmatter, fenced code blocks). Each parser's `parseLine` receives
/// the previous line's context and returns an updated context for the next line.
struct LineContext: Sendable, Equatable {

    /// `true` when inside a `---` delimited YAML frontmatter block.
    var inFrontmatter: Bool = false

    /// `true` when inside a fenced code block (` ``` ` or `~~~`).
    var inCodeBlock: Bool = false

    /// The fence string that opened the current code block (e.g. "```" or "~~~").
    var codeBlockFence: String? = nil

    /// The language hint after the opening fence (e.g. "swift", "kotlin").
    var codeBlockLanguage: String? = nil

    /// Fresh context for the first line of a document.
    static let initial = LineContext()
}

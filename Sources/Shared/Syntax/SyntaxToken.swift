// MARK: - SyntaxToken + SyntaxTokenKind
// Extensible token model for syntax highlighting.
// macOS 14+, Swift 5.10

import Foundation

/// Extensible token kind -- struct (not enum) for Open/Closed Principle.
///
/// New parsers add kinds via `extension SyntaxTokenKind` without modifying
/// this file. The struct-based approach allows arbitrary growth while
/// retaining `Hashable` and `Sendable` conformance.
struct SyntaxTokenKind: RawRepresentable, Hashable, Sendable {
    let rawValue: String
    init(rawValue: String) { self.rawValue = rawValue }
}

// MARK: - Built-in Token Kinds

extension SyntaxTokenKind {

    // Markdown
    static let heading         = SyntaxTokenKind(rawValue: "heading")
    static let bold            = SyntaxTokenKind(rawValue: "bold")
    static let italic          = SyntaxTokenKind(rawValue: "italic")
    static let inlineCode      = SyntaxTokenKind(rawValue: "inlineCode")
    static let codeBlockFence  = SyntaxTokenKind(rawValue: "codeBlockFence")
    static let codeBlockBody   = SyntaxTokenKind(rawValue: "codeBlockBody")
    static let link            = SyntaxTokenKind(rawValue: "link")
    static let linkURL         = SyntaxTokenKind(rawValue: "linkURL")
    static let blockquote      = SyntaxTokenKind(rawValue: "blockquote")
    static let listMarker      = SyntaxTokenKind(rawValue: "listMarker")
    static let horizontalRule  = SyntaxTokenKind(rawValue: "horizontalRule")

    // YAML Frontmatter
    static let frontmatterDelimiter = SyntaxTokenKind(rawValue: "frontmatterDelimiter")
    static let frontmatterKey       = SyntaxTokenKind(rawValue: "frontmatterKey")
    static let frontmatterValue     = SyntaxTokenKind(rawValue: "frontmatterValue")

    // CodeSpeak-specific
    static let csDirective = SyntaxTokenKind(rawValue: "csDirective")
    static let csFileRef   = SyntaxTokenKind(rawValue: "csFileRef")

    // Generic
    static let comment = SyntaxTokenKind(rawValue: "comment")
    static let plain   = SyntaxTokenKind(rawValue: "plain")
}

/// A single highlighted range produced by a ``SyntaxParsing`` implementation.
///
/// Uses `NSRange` for direct TextKit 1 / `NSTextStorage` compatibility.
struct SyntaxToken: Sendable {

    /// The semantic kind of this token (heading, bold, inline code, etc.).
    let kind: SyntaxTokenKind

    /// UTF-16 range in the full document string.
    let range: NSRange
}

// MARK: - CodeSpeakParser
// Syntax parser for CodeSpeak .cs.md files.
// macOS 14+, Swift 5.10

import Foundation

/// Composite parser for CodeSpeak `.cs.md` files.
///
/// Delegates to ``YAMLFrontmatterParser`` for `---` blocks and
/// ``MarkdownParser`` for standard markdown, then adds CodeSpeak-specific
/// tokens:
///
/// - `//` single-line comments (``SyntaxTokenKind/comment``)
struct CodeSpeakParser: SyntaxParsing, Sendable {

    let supportedExtensions: [String] = ["cs.md"]

    private let frontmatterParser = YAMLFrontmatterParser()
    private let markdownParser = MarkdownParser()

    func parseLine(
        _ line: String,
        lineRange: NSRange,
        context: LineContext
    ) -> (tokens: [SyntaxToken], nextContext: LineContext) {
        let trimmed = line.trimmingCharacters(in: .whitespaces)

        // Frontmatter delimiter
        if trimmed == "---" {
            return frontmatterParser.parseLine(
                line, lineRange: lineRange, context: context
            )
        }

        // Inside frontmatter
        if context.inFrontmatter {
            return frontmatterParser.parseLine(
                line, lineRange: lineRange, context: context
            )
        }

        // CodeSpeak // single-line comment
        if trimmed.hasPrefix("//") {
            let token = SyntaxToken(kind: .comment, range: lineRange)
            return ([token], context)
        }

        // Standard Markdown
        return markdownParser.parseLine(
            line, lineRange: lineRange, context: context
        )
    }
}

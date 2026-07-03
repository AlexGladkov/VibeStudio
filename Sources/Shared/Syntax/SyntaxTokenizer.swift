// MARK: - SyntaxTokenizer
// Shared line-by-line document tokenization used by both the live
// NSTextStorage highlighter and the CodeSpeak editor coordinator.
// macOS 14+, Swift 5.10

import Foundation

/// Stateless helper that tokenizes an entire document line by line using a
/// ``SyntaxParsing`` implementation.
///
/// Both ``SyntaxHighlightTextStorage`` and `CodeSpeakEditorView.Coordinator`
/// need to run the same full-document tokenization pass off the main actor
/// before applying attributes. This type is the single source of truth for
/// that walk so the two call sites cannot drift apart.
///
/// The walk uses `NSString.lineRange(for:)` so token ranges are expressed in
/// UTF-16 units matching `NSTextStorage` / `NSAttributedString` indexing.
/// A ``LineContext`` is threaded between lines so parsers can carry
/// multi-line state (e.g. open fenced code blocks).
enum SyntaxTokenizer {

    /// Tokenizes the entire document line by line.
    ///
    /// Safe to call off the main actor — it only reads the immutable `text`
    /// and the `Sendable` `parser`.
    ///
    /// - Parameters:
    ///   - text: Full document contents.
    ///   - parser: Parser applied to each line.
    /// - Returns: All tokens across every line, in document order.
    static func tokenizeAll(
        text: String,
        parser: any SyntaxParsing
    ) -> [SyntaxToken] {
        let nsText = text as NSString
        let fullLength = nsText.length
        var context = LineContext.initial
        var allTokens: [SyntaxToken] = []
        var pos = 0

        while pos < fullLength {
            let lineRange = nsText.lineRange(for: NSRange(location: pos, length: 0))

            var contentRange = lineRange
            if contentRange.length > 0 {
                let lastChar = nsText.character(at: NSMaxRange(contentRange) - 1)
                if lastChar == 0x0A || lastChar == 0x0D {
                    contentRange.length -= 1
                }
            }
            let lineContent = nsText.substring(with: contentRange)

            let (tokens, nextContext) = parser.parseLine(
                lineContent,
                lineRange: lineRange,
                context: context
            )
            allTokens.append(contentsOf: tokens)
            context = nextContext
            pos = NSMaxRange(lineRange)

            if pos == 0 { break }
        }

        return allTokens
    }
}

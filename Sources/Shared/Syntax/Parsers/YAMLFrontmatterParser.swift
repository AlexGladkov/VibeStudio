// MARK: - YAMLFrontmatterParser
// Syntax parser for YAML frontmatter blocks (--- delimited).
// macOS 14+, Swift 5.10

import Foundation

/// Parses YAML frontmatter between `---` delimiters.
///
/// Emits ``SyntaxTokenKind/frontmatterDelimiter``,
/// ``SyntaxTokenKind/frontmatterKey``, and
/// ``SyntaxTokenKind/frontmatterValue`` tokens.
///
/// Not a standalone parser -- used as a sub-parser by ``CodeSpeakParser``
/// and any future composite parser that needs frontmatter support.
struct YAMLFrontmatterParser: SyntaxParsing, Sendable {

    let supportedExtensions: [String] = [] // Not standalone

    func parseLine(
        _ line: String,
        lineRange: NSRange,
        context: LineContext
    ) -> (tokens: [SyntaxToken], nextContext: LineContext) {
        let trimmed = line.trimmingCharacters(in: .whitespaces)
        var ctx = context

        // Frontmatter delimiter
        if trimmed == "---" {
            ctx.inFrontmatter.toggle()
            return (
                [SyntaxToken(kind: .frontmatterDelimiter, range: lineRange)],
                ctx
            )
        }

        guard ctx.inFrontmatter else {
            return ([], ctx)
        }

        // Key: value parsing
        let nsLine = line as NSString
        if let colonRange = line.range(of: ":") {
            let keyEnd = line.distance(from: line.startIndex, to: colonRange.lowerBound)
            let keyNSRange = NSRange(
                location: lineRange.location,
                length: min(keyEnd, nsLine.length)
            )
            let valueStart = line.distance(from: line.startIndex, to: colonRange.upperBound)
            let valueLen = max(0, nsLine.length - valueStart)
            let valueNSRange = NSRange(
                location: lineRange.location + valueStart,
                length: valueLen
            )

            var tokens: [SyntaxToken] = []
            if keyNSRange.length > 0 {
                tokens.append(SyntaxToken(kind: .frontmatterKey, range: keyNSRange))
            }
            if valueNSRange.length > 0 {
                tokens.append(SyntaxToken(kind: .frontmatterValue, range: valueNSRange))
            }
            return (tokens, ctx)
        }

        // Plain frontmatter line (array items, etc.)
        return (
            [SyntaxToken(kind: .frontmatterValue, range: lineRange)],
            ctx
        )
    }
}

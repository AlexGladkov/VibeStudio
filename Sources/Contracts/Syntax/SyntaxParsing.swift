// MARK: - SyntaxParsing Protocol
// Stateless, Sendable syntax parser contract.
// macOS 14+, Swift 5.10

import Foundation

/// Stateless syntax parser. Implementations MUST be `Sendable`.
///
/// Parsing runs on a background thread via `Task.detached`; results are
/// applied on `MainActor`. The contract is line-by-line: `parseLine` is
/// called sequentially for each line in the document.
///
/// ## Adding a new parser
///
/// 1. Create a struct conforming to `SyntaxParsing & Sendable`.
/// 2. Return file extensions in `supportedExtensions`.
/// 3. Extend `SyntaxTokenKind` for any new token kinds.
/// 4. Register via `SyntaxParserRegistering.register(_:)` at app startup.
protocol SyntaxParsing: Sendable {

    /// File extensions handled by this parser (e.g. `["cs.md"]`, `["md"]`).
    ///
    /// Compound extensions (`cs.md`) are matched before simple ones (`md`).
    /// Return an empty array for sub-parsers that are not standalone
    /// (e.g. `YAMLFrontmatterParser`).
    var supportedExtensions: [String] { get }

    /// Parse a single line and return tokens plus updated context.
    ///
    /// - Parameters:
    ///   - line: Line content without trailing newline.
    ///   - lineRange: `NSRange` of this line in the full document string (UTF-16).
    ///   - context: Multiline state carried from the previous line.
    /// - Returns: Tuple of tokens produced for this line and the updated context.
    func parseLine(
        _ line: String,
        lineRange: NSRange,
        context: LineContext
    ) -> (tokens: [SyntaxToken], nextContext: LineContext)
}

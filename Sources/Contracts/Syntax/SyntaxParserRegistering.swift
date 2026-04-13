// MARK: - SyntaxParserRegistering Protocol
// DI-injectable registry for syntax parsers.
// macOS 14+, Swift 5.10

import Foundation

/// Registry that maps file extensions to ``SyntaxParsing`` implementations.
///
/// Populated at app startup (Composition Root). Thread-safe after initialization
/// because all mutations happen on `MainActor` before any views access it.
@MainActor
protocol SyntaxParserRegistering: AnyObject {

    /// Register a parser for its declared extensions.
    ///
    /// Each extension in `parser.supportedExtensions` is mapped to this parser.
    /// Registering a second parser for the same extension replaces the first.
    func register(_ parser: any SyntaxParsing)

    /// Find the parser for a file extension (e.g. "cs.md", "md").
    ///
    /// Compound extensions ("cs.md") match before simple ones ("md") via
    /// longest-match-first strategy.
    func parser(for fileExtension: String) -> (any SyntaxParsing)?
}

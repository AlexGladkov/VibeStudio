// MARK: - SyntaxParserRegistry
// Concrete registry for syntax parsers.
// macOS 14+, Swift 5.10

import Foundation
import OSLog

/// Concrete registry of syntax parsers, injectable via ``ServiceContainer``.
///
/// Compound extension lookup: `"cs.md"` is matched before `"md"` because
/// the caller tries the full extension first, then falls back to the last
/// component (longest-match-first strategy).
///
/// All mutations happen on `MainActor` at app startup. After the Composition
/// Root finishes registration, the registry is effectively read-only.
@Observable
@MainActor
final class SyntaxParserRegistry: SyntaxParserRegistering {

    // MARK: - Private State

    private var registry: [String: any SyntaxParsing] = [:]
    private let logger = Logger(subsystem: "com.vibestudio", category: "syntax")

    // MARK: - SyntaxParserRegistering

    func register(_ parser: any SyntaxParsing) {
        for ext in parser.supportedExtensions {
            registry[ext.lowercased()] = parser
            logger.debug("Registered syntax parser for .\(ext): \(String(describing: type(of: parser)))")
        }
    }

    func parser(for fileExtension: String) -> (any SyntaxParsing)? {
        let ext = fileExtension.lowercased()

        // Try as-is first (handles "cs.md" compound extension)
        if let parser = registry[ext] { return parser }

        // Try last component (handles "md" from "cs.md")
        let lastComponent = ext.split(separator: ".").last.map(String.init) ?? ext
        return registry[lastComponent]
    }
}

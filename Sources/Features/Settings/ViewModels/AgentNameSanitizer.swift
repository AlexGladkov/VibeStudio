// MARK: - AgentNameSanitizer
// Pure sanitisation of an agent name into a safe filename component.
// macOS 14+, Swift 5.10

import Foundation

/// Converts a free-form agent name into a safe filename component.
///
/// Rules (applied in order): lowercase, spaces → dashes, then keep only
/// ASCII letters, digits, `-` and `_`. Any other character (punctuation,
/// non-ASCII letters such as Cyrillic, path separators) is dropped. An empty
/// result signals an unusable name to the caller.
enum AgentNameSanitizer {

    /// Sanitise `name` into a filename-safe string. May return `""` when the
    /// input contains no permitted characters (e.g. a purely Cyrillic name).
    static func sanitize(_ name: String) -> String {
        name
            .lowercased()
            .replacingOccurrences(of: " ", with: "-")
            .filter { $0.isLetter && $0.isASCII || $0.isNumber || $0 == "-" || $0 == "_" }
    }
}

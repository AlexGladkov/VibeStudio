// MARK: - URLExtensions
// Convenience extensions for URL path formatting.
// macOS 14+, Swift 5.10

import Foundation

// MARK: - Tilde Abbreviation

extension URL {
    /// Returns the path with the home directory prefix replaced by `"~"`.
    ///
    /// Useful for displaying paths in the UI without exposing the full
    /// username path (e.g. `/Users/alice/.claude/CLAUDE.md` → `~/.claude/CLAUDE.md`).
    var tildeAbbreviatedPath: String {
        path.replacingOccurrences(
            of: FileManager.default.homeDirectoryForCurrentUser.path,
            with: "~"
        )
    }
}

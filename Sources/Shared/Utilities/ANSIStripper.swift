// MARK: - ANSIStripper
// Single source of truth for removing ANSI/VT escape sequences from terminal
// output. Previously two Terminal files carried divergent regex patterns:
//   - TerminalActivityTracker stripped CSI + charset-switch but NOT OSC
//   - TerminalService+Callbacks stripped a CSI subset + OSC but NOT charset
// This unifies both into one precompiled pattern that is a superset of each,
// so every call site strips *at least* as much as before.
// macOS 14+, Swift 5.10

import Foundation

/// Removes ANSI/VT escape sequences (CSI, OSC, charset switching, and other
/// single-character escapes) from a string.
///
/// Used both for shell-prompt detection (activity tracking) and for cleaning
/// captured buffer output before logging. Because the pattern is a *union* of
/// the previously separate patterns, it always strips more, never less — which
/// is safe for the "is the trimmed line empty?" checks that drive both call
/// sites.
enum ANSIStripper {

    /// Precompiled union pattern. Alternatives are ordered so that OSC (which
    /// spans until `BEL`) is matched before the catch-all single-character
    /// escape, otherwise `ESC ]` would be consumed as a lone escape and leave
    /// the OSC payload behind.
    private static let regex: NSRegularExpression? = try? NSRegularExpression(
        pattern: [
            "\\x1B\\][^\\x07]*\\x07",   // OSC: ESC ] ... BEL
            "\\x1B\\[[0-9;]*[A-Za-z]",  // CSI: ESC [ params final-letter
            "\\x1B[()][0-9A-Z]",        // charset switch: ESC ( / ) selector
            "\\x1B[^\\[()]"             // any other single-character escape
        ].joined(separator: "|")
    )

    /// Returns `s` with all ANSI/VT escape sequences removed. If the regex
    /// failed to compile (never expected for this static pattern), returns the
    /// input unchanged.
    static func strip(_ s: String) -> String {
        guard let regex else { return s }
        let range = NSRange(s.startIndex..., in: s)
        return regex.stringByReplacingMatches(in: s, range: range, withTemplate: "")
    }
}

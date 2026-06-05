// MARK: - SyntaxTokenBridge
// UTF-8 (Kotlin Int offsets) <-> UTF-16 (NSRange) bridge for syntax tokens.
// macOS 14+, Swift 5.10
//
// Why this exists
// ---------------
// Kotlin string offsets are **UTF-8 byte indices** (well, code-unit indices —
// for the JVM `String.length` and `substring` are UTF-16 code units, but the
// KMP parser layer in `studio.vibe.shared.contract.SyntaxParsing` exposes
// `start`/`end` as Int code-point-ish positions matching `String.indices`,
// which on Kotlin/Native are UTF-16-equivalent code units).
//
// Swift's `NSAttributedString` / `NSRange` use **UTF-16 code units**.
// `String.Index` uses extended grapheme clusters internally.
//
// In practice both Kotlin `String` (JVM and Native) and Swift `String.utf16`
// agree on UTF-16 code-unit offsets for the BMP and surrogate-pair handling.
// We therefore treat the KMP `Int` offset as a UTF-16 code-unit offset and
// build `NSRange` directly from it, *clamped* so that out-of-range tokens
// (e.g. from a stale parser run) never crash the text storage.

import Foundation

enum SyntaxTokenBridge {

    /// Build an `NSRange` from KMP `[start, end)` half-open UTF-16 offsets.
    ///
    /// - Parameters:
    ///   - start: Start offset (inclusive) in UTF-16 code units, as produced
    ///     by `studio.vibe.shared.contract.SyntaxParsing`.
    ///   - end:   End offset (exclusive) in UTF-16 code units.
    ///   - fullText: The exact source string the parser ran on. Used only for
    ///     bound clamping.
    /// - Returns: A valid `NSRange` clamped to the text's UTF-16 length.
    ///   If `start >= end` after clamping, returns `NSRange(location: start, length: 0)`.
    static func nsRange(start: Int, end: Int, in fullText: String) -> NSRange {
        let total = fullText.utf16.count
        let clampedStart = max(0, min(start, total))
        let clampedEnd = max(clampedStart, min(end, total))
        return NSRange(location: clampedStart, length: clampedEnd - clampedStart)
    }

    /// Inverse: extract `(start, end)` UTF-16 offsets from an `NSRange` for
    /// hand-off to KMP. Returned values are always non-negative.
    ///
    /// - Parameter range: Range within an NSAttributedString / NSTextStorage.
    /// - Returns: A `(start, end)` tuple where `end = location + length`.
    static func kmpOffsets(from range: NSRange) -> (start: Int, end: Int) {
        let s = max(0, range.location)
        let e = max(s, range.location + range.length)
        return (s, e)
    }

    /// Convenience: convert a Swift `String.Index` substring range into an
    /// `NSRange` suitable for `NSAttributedString`.
    ///
    /// Use this when the parser produces `Range<String.Index>` (grapheme
    /// clusters) and you need to apply attributes via UTF-16 ranges.
    static func nsRange(of substring: Range<String.Index>, in text: String) -> NSRange {
        return NSRange(substring, in: text)
    }

    // MARK: - Test stubs (inline — no XCTest target wired yet)
    //
    // When a unit-test target is added, lift the assertions below into
    // `Tests/SyntaxTokenBridgeTests.swift`. They cover:
    //
    //   1. ASCII round-trip:
    //      let r = nsRange(start: 2, end: 5, in: "abcdef")
    //      assert(r == NSRange(location: 2, length: 3))
    //
    //   2. Clamping past end:
    //      let r = nsRange(start: 4, end: 99, in: "abc")
    //      assert(r == NSRange(location: 3, length: 0))
    //
    //   3. Negative start:
    //      let r = nsRange(start: -5, end: 2, in: "abc")
    //      assert(r == NSRange(location: 0, length: 2))
    //
    //   4. Surrogate pair (emoji 👩‍💻 — multi-UTF-16 unit):
    //      let s = "x👩y"   // x=1, 👩=2, y=1 → utf16.count == 4
    //      let r = nsRange(start: 1, end: 3, in: s)
    //      assert(r == NSRange(location: 1, length: 2))
    //
    //   5. Inverse round-trip:
    //      let (a, b) = kmpOffsets(from: NSRange(location: 3, length: 4))
    //      assert(a == 3 && b == 7)
}

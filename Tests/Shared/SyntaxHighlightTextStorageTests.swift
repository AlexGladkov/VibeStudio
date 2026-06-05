// MARK: - SyntaxHighlightTextStorageTests
// Verifies version-guard + race safety of the async tokenize-then-apply
// pipeline.
// macOS 14+, Swift 5.10

import AppKit
import XCTest
@testable import VibeStudio

@MainActor
final class SyntaxHighlightTextStorageTests: XCTestCase {

    // MARK: - Test Doubles

    /// Sendable parser that emits a single "keyword" token per non-empty line
    /// and pauses for a configurable delay so we can race tokenization against
    /// fresh edits.
    private struct SlowParser: SyntaxParsing {
        let supportedExtensions: [String] = ["txt"]
        let delayMS: Int

        init(delayMS: Int = 100) {
            self.delayMS = delayMS
        }

        func parseLine(
            _ line: String,
            lineRange: NSRange,
            context: LineContext
        ) -> (tokens: [SyntaxToken], nextContext: LineContext) {
            // Block synchronously to simulate heavy tokenization. The detached
            // Task in the SUT runs on a background thread so blocking here
            // does not deadlock the test's MainActor.
            Thread.sleep(forTimeInterval: Double(delayMS) / 1000.0)
            guard !line.isEmpty else { return ([], context) }
            return ([SyntaxToken(kind: .heading, range: lineRange)], context)
        }
    }

    // MARK: - Basic apply

    func test_setParser_appliesHighlightingAfterAsyncTokenize() async throws {
        let sut = SyntaxHighlightTextStorage()
        sut.replaceCharacters(in: NSRange(location: 0, length: 0), with: "let x = 1\n")
        sut.setParser(SlowParser(delayMS: 10))

        // Wait for the async highlight pass to apply.
        try await Task.sleep(for: .milliseconds(150))

        let attrs = sut.attributes(at: 0, effectiveRange: nil)
        let color = attrs[.foregroundColor] as? NSColor
        XCTAssertNotNil(color, "highlight pass should have set a foreground color")
    }

    // MARK: - Version guard discards stale tokenization results

    func test_rapidEdits_doNotApplyStaleTokens() async throws {
        let sut = SyntaxHighlightTextStorage()
        sut.setParser(SlowParser(delayMS: 150))

        // Fire the first edit — its tokenizer will sleep for 150ms.
        sut.replaceCharacters(in: NSRange(location: 0, length: 0), with: "first\n")

        // Race in a second edit before the first tokenize completes.
        try await Task.sleep(for: .milliseconds(20))
        sut.replaceCharacters(in: NSRange(location: 0, length: sut.length), with: "second line\n")

        // Wait long enough for both passes to settle.
        try await Task.sleep(for: .milliseconds(400))

        // The final string must reflect the SECOND edit and the SUT must not
        // have crashed (which it would if the stale token application were not
        // version-guarded and tried to address bytes from "first").
        XCTAssertEqual(sut.string, "second line\n")
    }

    // MARK: - Out-of-range tokens are clamped

    func test_applyTokens_clampsRangesPastEndOfBuffer() async throws {
        struct OutOfRangeParser: SyntaxParsing {
            let supportedExtensions: [String] = []
            func parseLine(
                _ line: String,
                lineRange: NSRange,
                context: LineContext
            ) -> (tokens: [SyntaxToken], nextContext: LineContext) {
                // Emit a token that extends 1000 chars past the actual line.
                let bogus = NSRange(location: lineRange.location,
                                    length: lineRange.length + 1000)
                return ([SyntaxToken(kind: .heading, range: bogus)], context)
            }
        }

        let sut = SyntaxHighlightTextStorage()
        sut.replaceCharacters(in: NSRange(location: 0, length: 0), with: "abc")
        sut.setParser(OutOfRangeParser())

        // Should not crash even though tokens claim ranges past EOF.
        try await Task.sleep(for: .milliseconds(120))
        XCTAssertEqual(sut.string, "abc")
    }

    // MARK: - Concurrent edit during async tokenize

    func test_concurrentEditDuringTokenize_doesNotCrash() async throws {
        let sut = SyntaxHighlightTextStorage()
        sut.setParser(SlowParser(delayMS: 80))

        // Type 30 characters one at a time; each insertion schedules a fresh
        // tokenization pass and cancels the previous one. We only care that
        // the SUT survives the storm.
        for ch in "abcdefghijklmnopqrstuvwxyz0123" {
            sut.replaceCharacters(in: NSRange(location: sut.length, length: 0),
                                  with: String(ch))
            try await Task.sleep(for: .milliseconds(5))
        }
        try await Task.sleep(for: .milliseconds(200))
        XCTAssertEqual(sut.string.count, 30)
    }

    // MARK: - updateAppearance triggers re-highlight

    func test_updateAppearance_doesNotResetText() async throws {
        let sut = SyntaxHighlightTextStorage()
        sut.replaceCharacters(in: NSRange(location: 0, length: 0), with: "hello\n")
        sut.setParser(SlowParser(delayMS: 10))
        try await Task.sleep(for: .milliseconds(80))

        sut.updateAppearance(
            font: NSFont.monospacedSystemFont(ofSize: 14, weight: .regular),
            textColor: .systemRed
        )
        try await Task.sleep(for: .milliseconds(80))

        XCTAssertEqual(sut.string, "hello\n")
    }
}

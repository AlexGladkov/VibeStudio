import XCTest
@testable import VibeStudio

/// Unit tests for ``AgentNameSanitizer/sanitize(_:)`` — the pure name→filename
/// transform extracted from `AgentEditorViewModel`. Derives the `.md` filename
/// for new agents written into `~/.claude/agents/`.
///
/// Documented actual behaviour: lowercase → spaces to dashes → keep only
/// ASCII `[a-z0-9-_]`. Non-ASCII letters (e.g. Cyrillic) are dropped entirely,
/// which surfaces to the user as the "недопустимое имя" error in `save()`.
final class AgentNameSanitizerTests: XCTestCase {

    func testSpacesToDashesAndPunctuationDropped() {
        // "My Agent!" → lowercase "my agent!" → dashes "my-agent!" → drop "!".
        XCTAssertEqual(AgentNameSanitizer.sanitize("My Agent!"), "my-agent")
    }

    func testCyrillicDropsToEmpty() {
        // Cyrillic letters are Unicode letters but not ASCII → filtered out.
        // Empty result → caller reports the "недопустимое имя агента" error.
        XCTAssertEqual(AgentNameSanitizer.sanitize("Агент"), "")
    }

    func testAllowedCharactersPreserved() {
        // Lowercase ASCII letters, digits, dash and underscore pass through.
        XCTAssertEqual(AgentNameSanitizer.sanitize("a_b-c"), "a_b-c")
    }

    func testDigitsPreserved() {
        XCTAssertEqual(AgentNameSanitizer.sanitize("agent2"), "agent2")
    }

    func testSpecialSymbolsFiltered() {
        // Slashes, dots and other punctuation are removed (path-traversal safe).
        XCTAssertEqual(AgentNameSanitizer.sanitize("../foo.bar"), "foobar")
    }

    func testUppercaseLowercased() {
        XCTAssertEqual(AgentNameSanitizer.sanitize("BUILDER"), "builder")
    }

    func testMixedCyrillicAndAsciiKeepsAsciiOnly() {
        // "code-агент" → "code-" (Cyrillic dropped, dash & ascii kept).
        XCTAssertEqual(AgentNameSanitizer.sanitize("code-агент"), "code-")
    }
}

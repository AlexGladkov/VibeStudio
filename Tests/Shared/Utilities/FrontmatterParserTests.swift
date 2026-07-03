import XCTest
@testable import VibeStudio

/// Unit tests for ``parseFrontmatter(_:)`` — YAML-ish frontmatter extraction.
/// Pure string parsing, no FS.
///
/// Documented actual behaviours that differ from a naive expectation:
/// - `user_invocable` is `true` ONLY for a case-insensitive `true`. Values like
///   `yes` / `1` map to `false`.
/// - Value splitting is by the field prefix length (`name:` etc.), so any `:`
///   inside the value is preserved.
/// - Line splitting is on `\n` only. A pure-CRLF document has `---\r` as its
///   first line, which fails the opening `---` guard → ALL fields empty. A
///   trailing `\r` inside a value line is NOT trimmed (\r is not in
///   `.whitespaces`) and remains in the extracted value.
final class FrontmatterParserTests: XCTestCase {

    // MARK: - Missing / malformed delimiter

    func testNoLeadingDelimiterReturnsEmptyFields() {
        let fields = parseFrontmatter("name: foo\ndescription: bar\n")
        XCTAssertEqual(fields.name, "")
        XCTAssertEqual(fields.description, "")
        XCTAssertFalse(fields.userInvocable)
    }

    func testEmptyContentReturnsEmptyFields() {
        let fields = parseFrontmatter("")
        XCTAssertEqual(fields.name, "")
        XCTAssertEqual(fields.description, "")
        XCTAssertFalse(fields.userInvocable)
    }

    // MARK: - Basic extraction

    func testExtractsNameDescriptionInvocable() {
        let content = """
        ---
        name: my-agent
        description: Does a thing
        user_invocable: true
        ---
        body text
        """
        let fields = parseFrontmatter(content)
        XCTAssertEqual(fields.name, "my-agent")
        XCTAssertEqual(fields.description, "Does a thing")
        XCTAssertTrue(fields.userInvocable)
    }

    // MARK: - user_invocable truthiness

    func testUserInvocableTrueLowercase() {
        XCTAssertTrue(parseFrontmatter("---\nuser_invocable: true\n---").userInvocable)
    }

    func testUserInvocableTrueUppercaseIsTrue() {
        // lowercased() == "true" → TRUE also matches.
        XCTAssertTrue(parseFrontmatter("---\nuser_invocable: TRUE\n---").userInvocable)
    }

    func testUserInvocableYesIsFalse() {
        // ACTUAL: only "true" (case-insensitive) counts; "yes" → false.
        XCTAssertFalse(parseFrontmatter("---\nuser_invocable: yes\n---").userInvocable)
    }

    func testUserInvocableFalseIsFalse() {
        XCTAssertFalse(parseFrontmatter("---\nuser_invocable: false\n---").userInvocable)
    }

    // MARK: - Colon inside value

    func testColonInsideValuePreserved() {
        let content = "---\ndescription: title: subtitle\n---"
        // Split is by "description:".count, so everything after the first
        // colon (trimmed) is the value, including the inner colon.
        XCTAssertEqual(parseFrontmatter(content).description, "title: subtitle")
    }

    // MARK: - Missing closing delimiter

    func testMissingClosingDelimiterStillExtracts() {
        let content = "---\nname: solo\ndescription: no close"
        let fields = parseFrontmatter(content)
        XCTAssertEqual(fields.name, "solo")
        XCTAssertEqual(fields.description, "no close")
    }

    // MARK: - Whitespace / tabs

    func testTabsAndSpacesAroundValueTrimmed() {
        let content = "---\nname:  \tspaced\t \ndescription:\ttabbed\n---"
        let fields = parseFrontmatter(content)
        XCTAssertEqual(fields.name, "spaced")
        XCTAssertEqual(fields.description, "tabbed")
    }

    // MARK: - CRLF handling (documented actual)

    func testPureCRLFFailsOpeningGuard() {
        // First line becomes "---\r"; \r is not whitespace, so the guard
        // `first == "---"` fails and every field is empty.
        let content = "---\r\nname: foo\r\ndescription: bar\r\n---\r\n"
        let fields = parseFrontmatter(content)
        XCTAssertEqual(fields.name, "")
        XCTAssertEqual(fields.description, "")
        XCTAssertFalse(fields.userInvocable)
    }

    func testTrailingCarriageReturnRemainsInValue() {
        // LF opening line passes the guard, but a value line ending in \r
        // keeps the \r because it is not trimmed by .whitespaces.
        let content = "---\nname: foo\r\n---\n"
        XCTAssertEqual(parseFrontmatter(content).name, "foo\r")
    }
}

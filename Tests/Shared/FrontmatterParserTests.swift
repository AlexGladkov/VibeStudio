// MARK: - FrontmatterParserTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

final class FrontmatterParserTests: XCTestCase {

    func test_noFrontmatter_returnsEmptyFields() {
        let result = parseFrontmatter("# Just a heading\n\nBody.")
        XCTAssertEqual(result.name, "")
        XCTAssertEqual(result.description, "")
        XCTAssertFalse(result.userInvocable)
    }

    func test_parsesAllFields() {
        let content = """
        ---
        name: my-agent
        description: An example agent.
        user_invocable: true
        ---
        # Body
        """
        let result = parseFrontmatter(content)
        XCTAssertEqual(result.name, "my-agent")
        XCTAssertEqual(result.description, "An example agent.")
        XCTAssertTrue(result.userInvocable)
    }

    func test_userInvocable_falseWhenNotTrue() {
        let content = """
        ---
        name: x
        user_invocable: false
        ---
        """
        XCTAssertFalse(parseFrontmatter(content).userInvocable)
    }

    func test_userInvocable_caseInsensitive() {
        let content = """
        ---
        user_invocable: TRUE
        ---
        """
        XCTAssertTrue(parseFrontmatter(content).userInvocable)
    }

    func test_missingClosingDelimiter_parsesUntilEOF() {
        let content = """
        ---
        name: orphan
        description: no end marker
        """
        let result = parseFrontmatter(content)
        XCTAssertEqual(result.name, "orphan")
        XCTAssertEqual(result.description, "no end marker")
    }

    func test_emptyValues() {
        let content = """
        ---
        name:
        description:
        ---
        """
        let result = parseFrontmatter(content)
        XCTAssertEqual(result.name, "")
        XCTAssertEqual(result.description, "")
    }

    func test_extraWhitespace_trimmed() {
        let content = """
        ---
        name:    spaced-name
        ---
        """
        XCTAssertEqual(parseFrontmatter(content).name, "spaced-name")
    }

    func test_firstLineMustBeDashDashDash() {
        let content = """

        ---
        name: ignored
        ---
        """
        let result = parseFrontmatter(content)
        XCTAssertEqual(result.name, "", "Frontmatter not at line 1 must be ignored")
    }

    func test_emptyContent() {
        let result = parseFrontmatter("")
        XCTAssertEqual(result.name, "")
        XCTAssertEqual(result.description, "")
        XCTAssertFalse(result.userInvocable)
    }
}

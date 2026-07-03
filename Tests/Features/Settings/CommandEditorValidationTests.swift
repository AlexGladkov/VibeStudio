import XCTest
@testable import VibeStudio

/// Unit tests for ``CommandEditorViewModel/validateFilename(_:)`` — the pure
/// filename validator gating writes into `~/.claude/commands/`.
///
/// Security focus: the filename is user-supplied and becomes a path component,
/// so anything outside `[a-z0-9_-]` (path separators, `..`, dots, uppercase,
/// spaces) must be rejected to prevent path traversal. Path-traversal vectors
/// are asserted explicitly.
@MainActor
final class CommandEditorValidationTests: XCTestCase {

    private typealias Err = CommandEditorViewModel.FilenameValidationError

    private func assertFailure(_ input: String, _ expected: Err,
                               file: StaticString = #filePath, line: UInt = #line) {
        switch CommandEditorViewModel.validateFilename(input) {
        case .success(let name):
            XCTFail("Expected failure(\(expected)) but got success(\(name))", file: file, line: line)
        case .failure(let error):
            XCTAssertEqual(error, expected, file: file, line: line)
        }
    }

    private func assertSuccess(_ input: String, _ expected: String,
                               file: StaticString = #filePath, line: UInt = #line) {
        switch CommandEditorViewModel.validateFilename(input) {
        case .success(let name):
            XCTAssertEqual(name, expected, file: file, line: line)
        case .failure(let error):
            XCTFail("Expected success(\(expected)) but got failure(\(error))", file: file, line: line)
        }
    }

    // MARK: - Empty

    func testEmptyStringIsEmptyError() {
        assertFailure("", .empty)
    }

    func testWhitespaceOnlyTrimsToEmpty() {
        assertFailure("   ", .empty)
    }

    // MARK: - Path traversal (security)

    func testDotDotSlashRejected() {
        assertFailure("../etc", .invalidCharacters)
    }

    func testBareDotDotRejected() {
        assertFailure("..", .invalidCharacters)
    }

    func testForwardSlashRejected() {
        assertFailure("foo/bar", .invalidCharacters)
    }

    func testNestedTraversalRejected() {
        assertFailure("a/../b", .invalidCharacters)
    }

    func testAbsolutePathRejected() {
        assertFailure("/etc/passwd", .invalidCharacters)
    }

    // MARK: - Character-set violations

    func testUppercaseRejected() {
        assertFailure("Foo", .invalidCharacters)
    }

    func testInternalSpaceRejected() {
        assertFailure("my cmd", .invalidCharacters)
    }

    func testDotInNameRejected() {
        assertFailure("cmd.md", .invalidCharacters)
    }

    // MARK: - Valid names

    func testValidNameWithDashUnderscoreDigit() {
        assertSuccess("my-cmd_1", "my-cmd_1")
    }

    func testSurroundingWhitespaceTrimmed() {
        assertSuccess("  deploy  ", "deploy")
    }
}

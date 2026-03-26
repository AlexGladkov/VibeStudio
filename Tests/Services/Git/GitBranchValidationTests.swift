import XCTest
@testable import VibeStudio

final class GitBranchValidationTests: XCTestCase {

    private var sut: GitService!

    override func setUp() {
        super.setUp()
        sut = GitService()
    }

    override func tearDown() {
        sut = nil
        super.tearDown()
    }

    // MARK: - Valid Branch Names

    func testValidSimpleBranch() async {
        await assertDoesNotThrow { try await self.sut.validateBranchName("main") }
    }

    func testValidFeatureBranch() async {
        await assertDoesNotThrow { try await self.sut.validateBranchName("feature/my-thing") }
    }

    func testValidReleaseBranch() async {
        await assertDoesNotThrow { try await self.sut.validateBranchName("release/1.0.0") }
    }

    func testValidFixBranch() async {
        await assertDoesNotThrow { try await self.sut.validateBranchName("fix/bug-123") }
    }

    func testValidBranchWithDots() async {
        await assertDoesNotThrow { try await self.sut.validateBranchName("v1.2.3") }
    }

    func testValidBranchWithAt() async {
        await assertDoesNotThrow { try await self.sut.validateBranchName("user@feature") }
    }

    func testValidOriginRemoteName() async {
        await assertDoesNotThrow { try await self.sut.validateBranchName("origin") }
    }

    // MARK: - Invalid Branch Names

    func testInvalidBranchStartsWithDash() async {
        await assertThrowsGitError { try await self.sut.validateBranchName("--evil-flag") }
    }

    func testInvalidBranchWithSpaces() async {
        await assertThrowsGitError { try await self.sut.validateBranchName("branch with spaces") }
    }

    func testInvalidBranchDoubleDots() async {
        await assertThrowsGitError { try await self.sut.validateBranchName("ref..other") }
    }

    func testInvalidBranchTilde() async {
        await assertThrowsGitError { try await self.sut.validateBranchName("ref~1") }
    }

    func testInvalidBranchCaret() async {
        await assertThrowsGitError { try await self.sut.validateBranchName("ref^1") }
    }

    func testInvalidBranchColon() async {
        await assertThrowsGitError { try await self.sut.validateBranchName("ref:other") }
    }

    func testInvalidBranchBackslash() async {
        await assertThrowsGitError { try await self.sut.validateBranchName("ref\\other") }
    }

    func testEmptyBranchName() async {
        await assertThrowsGitError { try await self.sut.validateBranchName("") }
    }

    func testInvalidBranchWithSpecialChars() async {
        await assertThrowsGitError { try await self.sut.validateBranchName("branch!name") }
    }

    func testInvalidBranchSemicolon() async {
        // Injection attempt: "main; rm -rf /"
        await assertThrowsGitError { try await self.sut.validateBranchName("main; rm -rf /") }
    }

    func testInvalidBranchDollarSign() async {
        await assertThrowsGitError { try await self.sut.validateBranchName("$HOME") }
    }

    func testInvalidBranchBackticks() async {
        await assertThrowsGitError { try await self.sut.validateBranchName("`whoami`") }
    }

    // MARK: - Error Type Verification

    func testInvalidBranchThrowsCommandFailed() async {
        do {
            try await sut.validateBranchName("--evil")
            XCTFail("Expected error to be thrown")
        } catch let error as GitServiceError {
            if case .commandFailed(let command, let exitCode, let stderr) = error {
                XCTAssertEqual(command, "validate")
                XCTAssertEqual(exitCode, 1)
                XCTAssertTrue(stderr.contains("Invalid branch name"))
            } else {
                XCTFail("Expected .commandFailed, got \(error)")
            }
        } catch {
            XCTFail("Unexpected error type: \(error)")
        }
    }

    // MARK: - Helpers

    private func assertDoesNotThrow(
        _ expression: @escaping () async throws -> Void,
        file: StaticString = #filePath,
        line: UInt = #line
    ) async {
        do {
            try await expression()
        } catch {
            XCTFail("Unexpected error: \(error)", file: file, line: line)
        }
    }

    private func assertThrowsGitError(
        _ expression: @escaping () async throws -> Void,
        file: StaticString = #filePath,
        line: UInt = #line
    ) async {
        do {
            try await expression()
            XCTFail("Expected error to be thrown", file: file, line: line)
        } catch is GitServiceError {
            // Expected
        } catch {
            XCTFail("Expected GitServiceError, got \(type(of: error)): \(error)", file: file, line: line)
        }
    }
}

// MARK: - CreateBranchViewModelTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

@MainActor
final class CreateBranchViewModelTests: XCTestCase {

    private let project = Project(name: "demo", path: URL(fileURLWithPath: "/tmp/demo"))

    func test_canCreate_falseForEmpty_trueForNonEmpty() {
        let sut = CreateBranchViewModel(gitService: MockGitService(), project: project)
        XCTAssertFalse(sut.canCreate)
        sut.branchName = "feature/x"
        XCTAssertTrue(sut.canCreate)
    }

    func test_create_blankName_returnsFalseWithoutCallingGit() async {
        let git = MockGitService()
        let sut = CreateBranchViewModel(gitService: git, project: project)
        sut.branchName = "   "
        let ok = await sut.create()
        XCTAssertFalse(ok)
        let count = await git.createBranchCallCount
        XCTAssertEqual(count, 0)
    }

    func test_create_happyPath_callsGit() async {
        let git = MockGitService()
        let sut = CreateBranchViewModel(gitService: git, project: project, fromBranch: "main")
        sut.branchName = "feature/x"

        let ok = await sut.create()
        XCTAssertTrue(ok)
        let count = await git.createBranchCallCount
        XCTAssertEqual(count, 1)
        XCTAssertNil(sut.errorMessage)
        XCTAssertFalse(sut.isCreating)
    }

    func test_create_errorPath_setsErrorMessage() async {
        let git = MockGitService()
        await git.setError()

        let sut = CreateBranchViewModel(gitService: git, project: project)
        sut.branchName = "feature/x"
        let ok = await sut.create()
        XCTAssertFalse(ok)
        XCTAssertNotNil(sut.errorMessage)
    }
}

private extension MockGitService {
    func setError() {
        createBranchErrorToThrow = GitServiceError.commandFailed(command: "branch", exitCode: 1, stderr: "denied")
    }
}

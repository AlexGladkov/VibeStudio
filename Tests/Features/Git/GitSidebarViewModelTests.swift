import XCTest
@testable import VibeStudio

/// Unit tests for ``GitSidebarViewModel/loadGitInfo(for:)`` error mapping across
/// the three independent steps (status, branches, remote URL). Uses
/// ``MockGitService`` / ``MockProjectManager`` so no real git process runs.
///
/// Covered branches:
/// - status success populates caches and clears `nonGitProjects`
/// - `.notARepository` moves the project into `nonGitProjects` and returns early
/// - a non-`notARepository` status error records `projectBranchErrors`
/// - a branches failure with a non-empty status branch falls back to a single
///   current branch and flags `remoteUnavailableProjects`
@MainActor
final class GitSidebarViewModelTests: XCTestCase {

    private let repo = URL(fileURLWithPath: "/tmp/repo")

    // MARK: - Fixtures

    private func makeProject() -> Project {
        Project(name: "repo", path: repo)
    }

    private func makeSUT(git: MockGitService) -> GitSidebarViewModel {
        GitSidebarViewModel(
            gitService: git,
            aiCommitService: MockAICommitService(),
            projectManager: MockProjectManager()
        )
    }

    private func status(branch: String) -> GitStatus {
        GitStatus(
            branch: branch,
            aheadCount: 0,
            behindCount: 0,
            stagedFiles: [],
            unstagedFiles: [],
            untrackedFiles: []
        )
    }

    // MARK: - Status success

    func testStatusSuccessPopulatesCaches() async {
        let git = MockGitService()
        let expectedStatus = status(branch: "main")
        let expectedBranches = [
            GitBranch(name: "main", isRemote: false, isCurrent: true),
            GitBranch(name: "dev", isRemote: false, isCurrent: false)
        ]
        await git.setStatusResult(.success(expectedStatus))
        await git.setBranchesResult(.success(expectedBranches))
        let sut = makeSUT(git: git)
        let project = makeProject()

        await sut.loadGitInfo(for: project)

        XCTAssertEqual(sut.projectGitStatuses[project.id], expectedStatus)
        XCTAssertEqual(sut.projectBranches[project.id], expectedBranches)
        XCTAssertFalse(sut.nonGitProjects.contains(project.id))
        XCTAssertFalse(sut.remoteUnavailableProjects.contains(project.id))
        XCTAssertNil(sut.projectBranchErrors[project.id])
    }

    // MARK: - notARepository

    func testNotARepositoryMovesProjectIntoNonGit() async {
        let git = MockGitService()
        await git.setStatusResult(.failure(GitServiceError.notARepository(path: repo)))
        let sut = makeSUT(git: git)
        let project = makeProject()

        await sut.loadGitInfo(for: project)

        XCTAssertTrue(sut.nonGitProjects.contains(project.id))
        XCTAssertNil(sut.projectGitStatuses[project.id])
        XCTAssertEqual(sut.projectBranches[project.id], [], "early return still seeds an empty branch list")
        XCTAssertNil(sut.projectBranchErrors[project.id])
        // Branch fetch must be skipped after the early return.
        let branchesCalls = await git.branchesCallCount
        XCTAssertEqual(branchesCalls, 0)
    }

    // MARK: - Other status error

    func testOtherStatusErrorRecordsBranchError() async {
        let git = MockGitService()
        let gitError = GitServiceError.commandFailed(command: "status", exitCode: 1, stderr: "boom")
        await git.setStatusResult(.failure(gitError))
        let sut = makeSUT(git: git)
        let project = makeProject()

        await sut.loadGitInfo(for: project)

        XCTAssertEqual(sut.projectBranchErrors[project.id], gitError.localizedDescription)
        XCTAssertFalse(sut.nonGitProjects.contains(project.id))
        XCTAssertEqual(sut.projectBranches[project.id], [])
    }

    // MARK: - Branches failure fallback

    func testBranchesFailureWithBranchFallsBackToCurrentBranch() async {
        let git = MockGitService()
        await git.setStatusResult(.success(status(branch: "feature/x")))
        await git.setBranchesResult(.failure(GitServiceError.gitNotFound))
        let sut = makeSUT(git: git)
        let project = makeProject()

        await sut.loadGitInfo(for: project)

        XCTAssertTrue(sut.remoteUnavailableProjects.contains(project.id))
        XCTAssertEqual(
            sut.projectBranches[project.id],
            [GitBranch(name: "feature/x", isRemote: false, isCurrent: true)],
            "fallback should synthesise the current branch from the status branch"
        )
    }

    func testBranchesFailureWithEmptyBranchYieldsEmptyList() async {
        let git = MockGitService()
        await git.setStatusResult(.success(status(branch: "")))
        await git.setBranchesResult(.failure(GitServiceError.gitNotFound))
        let sut = makeSUT(git: git)
        let project = makeProject()

        await sut.loadGitInfo(for: project)

        XCTAssertTrue(sut.remoteUnavailableProjects.contains(project.id))
        XCTAssertEqual(sut.projectBranches[project.id], [])
    }
}

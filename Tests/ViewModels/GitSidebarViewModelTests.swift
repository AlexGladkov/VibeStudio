// MARK: - GitSidebarViewModelTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

@MainActor
final class GitSidebarViewModelTests: XCTestCase {

    private var gitService: MockGitService!
    private var aiService: MockAICommitService!
    private var project: Project!

    override func setUp() {
        super.setUp()
        gitService = MockGitService()
        aiService = MockAICommitService()
        project = Project(name: "demo", path: URL(fileURLWithPath: "/tmp/demo"))
    }

    override func tearDown() {
        gitService = nil
        aiService = nil
        project = nil
        super.tearDown()
    }

    private func makeSUT() -> GitSidebarViewModel {
        GitSidebarViewModel(gitService: gitService, aiCommitService: aiService)
    }

    // MARK: - loadGitInfo

    func test_loadGitInfo_success_populatesStatusAndBranches() async {
        let status = GitStatus(
            branch: "main",
            aheadCount: 0, behindCount: 0,
            stagedFiles: [], unstagedFiles: [], untrackedFiles: []
        )
        await gitService.setStatusResult(.success(status))
        await gitService.setBranchesResult(.success([
            GitBranch(name: "main", isRemote: false, isCurrent: true)
        ]))

        let sut = makeSUT()
        await sut.loadGitInfo(for: project)

        XCTAssertEqual(sut.projectGitStatuses[project.id]?.branch, "main")
        XCTAssertEqual(sut.projectBranches[project.id]?.count, 1)
        XCTAssertFalse(sut.nonGitProjects.contains(project.id))
    }

    func test_loadGitInfo_notARepository_marksProjectAsNonGit() async {
        await gitService.setStatusResult(.failure(GitServiceError.notARepository(path: project.path)))

        let sut = makeSUT()
        await sut.loadGitInfo(for: project)

        XCTAssertTrue(sut.nonGitProjects.contains(project.id))
        XCTAssertNil(sut.projectGitStatuses[project.id])
    }

    func test_loadGitInfo_branchesFailure_keepsStatusAndFallsBack() async {
        let status = GitStatus(
            branch: "feature/x",
            aheadCount: 0, behindCount: 0,
            stagedFiles: [], unstagedFiles: [], untrackedFiles: []
        )
        await gitService.setStatusResult(.success(status))
        struct Boom: Error {}
        await gitService.setBranchesResult(.failure(Boom()))

        let sut = makeSUT()
        await sut.loadGitInfo(for: project)

        XCTAssertTrue(sut.remoteUnavailableProjects.contains(project.id))
        XCTAssertEqual(sut.projectBranches[project.id]?.first?.name, "feature/x",
                       "Falls back to current-branch-only list")
    }

    // MARK: - performCommit

    func test_performCommit_emptySummary_setsError() async {
        let sut = makeSUT()
        sut.commitSummaries[project.id] = "  "
        await sut.performCommit(for: project)
        XCTAssertEqual(sut.commitPanelErrors[project.id], "Commit summary cannot be empty")
    }

    func test_performCommit_success_clearsSummaryAndCommits() async {
        let sut = makeSUT()
        sut.commitSummaries[project.id] = "feat: thing"
        sut.commitDescriptions[project.id] = "details"
        await sut.performCommit(for: project)

        let count = await gitService.commitCallCount
        XCTAssertEqual(count, 1)
        let msg = await gitService.lastCommitMessage
        XCTAssertEqual(msg, "feat: thing\n\ndetails")
        XCTAssertNil(sut.commitSummaries[project.id])
        XCTAssertNil(sut.commitDescriptions[project.id])
    }

    // MARK: - cleanupProject

    func test_cleanupProject_removesAllPerProjectState() {
        let sut = makeSUT()
        sut.gitExpandedProjects.insert(project.id)
        sut.projectGitStatuses[project.id] = .empty
        sut.commitSummaries[project.id] = "x"
        sut.nonGitProjects.insert(project.id)

        sut.cleanupProject(project.id)

        XCTAssertFalse(sut.gitExpandedProjects.contains(project.id))
        XCTAssertNil(sut.projectGitStatuses[project.id])
        XCTAssertNil(sut.commitSummaries[project.id])
        XCTAssertFalse(sut.nonGitProjects.contains(project.id))
    }

    // MARK: - AI commit

    func test_generateAICommitMessage_noChanges_setsError() async {
        await gitService.setHeadDiffResult(.success("   \n\n"))
        let sut = makeSUT()
        await sut.generateAICommitMessage(for: project)
        XCTAssertEqual(sut.commitPanelErrors[project.id], "No changes to analyze")
        XCTAssertFalse(sut.showAIDiffWarning)
    }

    func test_generateAICommitMessage_hasChanges_showsWarning() async {
        await gitService.setHeadDiffResult(.success("diff --git a/x b/x\n+new line"))
        let sut = makeSUT()
        await sut.generateAICommitMessage(for: project)
        XCTAssertTrue(sut.showAIDiffWarning)
        XCTAssertNotNil(sut.pendingAIDiffText)
    }

    func test_sendAIDiff_splitsSummaryAndDescription() async {
        await aiService.setGenerateResult(.success("feat: do thing\n\nLonger body line."))
        let sut = makeSUT()
        await sut.sendAIDiff("diff body", for: project)
        XCTAssertEqual(sut.commitSummaries[project.id], "feat: do thing")
        XCTAssertEqual(sut.commitDescriptions[project.id], "Longer body line.")
    }
}

// MARK: - Test helpers — MockGitService setters
// (MockGitService is an actor; the underlying stored properties cannot be
// mutated synchronously from @MainActor tests. These small helpers keep
// call sites concise without changing the mock surface for existing tests.)

extension MockGitService {
    func setStatusResult(_ r: Result<GitStatus, Error>) { statusResult = r }
    func setBranchesResult(_ r: Result<[GitBranch], Error>) { branchesResult = r }
    func setHeadDiffResult(_ r: Result<String, Error>) { headDiffResult = r }
}

import XCTest
@testable import VibeStudio

/// Unit tests for ``GitCommitViewModel`` commit flow and AI commit-message
/// generation. Uses ``MockGitService`` and ``MockAICommitService`` so no real
/// git process or network call is made.
///
/// Covered branches:
/// - empty summary short-circuits to an error without invoking `commit`
/// - summary + description merge with a blank line separator
/// - successful commit clears per-project input and invokes `onCommitted`
/// - `GitServiceError.commandFailed` surfaces the trimmed stderr
/// - `generateAICommitMessage` reports "No changes" on an empty diff
/// - `sendAIDiff` splits summary / description and truncates the diff payload
@MainActor
final class GitCommitViewModelTests: XCTestCase {

    private let repo = URL(fileURLWithPath: "/tmp/repo")

    // MARK: - Fixtures

    private func makeProject() -> Project {
        Project(name: "repo", path: repo)
    }

    private func makeSUT(
        git: MockGitService = MockGitService(),
        aiService: MockAICommitService = MockAICommitService()
    ) -> GitCommitViewModel {
        GitCommitViewModel(gitService: git, aiCommitService: aiService)
    }

    // MARK: - Empty Summary Guard

    func testEmptySummaryReportsErrorAndSkipsCommit() async {
        let git = MockGitService()
        let sut = makeSUT(git: git)
        let project = makeProject()
        // Only whitespace — should be treated as empty after trimming.
        sut.commitSummaries[project.id] = "   \n  "

        await sut.performCommit(for: project)

        XCTAssertEqual(sut.commitPanelErrors[project.id], "Commit summary cannot be empty")
        let commitCalls = await git.commitCallCount
        let stageCalls = await git.stageCallCount
        XCTAssertEqual(commitCalls, 0, "commit must not run for an empty summary")
        XCTAssertEqual(stageCalls, 0, "stage must not run for an empty summary")
    }

    // MARK: - Message Composition

    func testSummaryAndDescriptionMergeWithBlankLine() async {
        let git = MockGitService()
        let sut = makeSUT(git: git)
        let project = makeProject()
        sut.commitSummaries[project.id] = "feat: add widget"
        sut.commitDescriptions[project.id] = "Longer body\nsecond line"

        await sut.performCommit(for: project)

        let message = await git.lastCommitMessage
        XCTAssertEqual(message, "feat: add widget\n\nLonger body\nsecond line")
    }

    func testEmptyDescriptionCommitsSummaryOnly() async {
        let git = MockGitService()
        let sut = makeSUT(git: git)
        let project = makeProject()
        sut.commitSummaries[project.id] = "chore: bump"
        // No description set.

        await sut.performCommit(for: project)

        let message = await git.lastCommitMessage
        XCTAssertEqual(message, "chore: bump")
    }

    // MARK: - Successful Commit

    func testSuccessfulCommitClearsInputAndInvokesOnCommitted() async {
        let git = MockGitService()
        let sut = makeSUT(git: git)
        let project = makeProject()
        sut.commitSummaries[project.id] = "feat: done"
        sut.commitDescriptions[project.id] = "body"

        var committedProject: Project?
        sut.onCommitted = { project in committedProject = project }

        await sut.performCommit(for: project)

        XCTAssertNil(sut.commitSummaries[project.id], "summary should be cleared after success")
        XCTAssertNil(sut.commitDescriptions[project.id], "description should be cleared after success")
        XCTAssertNil(sut.commitPanelErrors[project.id])
        XCTAssertFalse(sut.committingProjects.contains(project.id), "in-progress flag must be released")
        XCTAssertEqual(committedProject?.id, project.id, "onCommitted must receive the committed project")
    }

    // MARK: - commandFailed stderr extraction

    func testCommandFailedSurfacesTrimmedStderr() async {
        let git = MockGitService()
        await git.setCommitResult(.failure(
            GitServiceError.commandFailed(command: "commit", exitCode: 1, stderr: "  nothing to commit  \n")
        ))
        let sut = makeSUT(git: git)
        let project = makeProject()
        sut.commitSummaries[project.id] = "feat: x"

        await sut.performCommit(for: project)

        XCTAssertEqual(sut.commitPanelErrors[project.id], "nothing to commit")
        // Input is preserved on failure so the user can retry.
        XCTAssertEqual(sut.commitSummaries[project.id], "feat: x")
        XCTAssertFalse(sut.committingProjects.contains(project.id))
    }

    func testNonCommandFailedGitErrorUsesLocalizedDescription() async {
        let git = MockGitService()
        await git.setCommitResult(.failure(GitServiceError.gitNotFound))
        let sut = makeSUT(git: git)
        let project = makeProject()
        sut.commitSummaries[project.id] = "feat: x"

        await sut.performCommit(for: project)

        XCTAssertEqual(sut.commitPanelErrors[project.id], GitServiceError.gitNotFound.localizedDescription)
    }

    // MARK: - generateAICommitMessage

    func testGenerateAIWithEmptyDiffReportsNoChanges() async {
        let git = MockGitService()
        // Whitespace-only diff must be treated as "no changes".
        await git.setHeadDiffResult(.success("   \n\t"))
        let sut = makeSUT(git: git)
        let project = makeProject()

        await sut.generateAICommitMessage(for: project)

        XCTAssertEqual(sut.commitPanelErrors[project.id], "No changes to analyze")
        XCTAssertFalse(sut.showAIDiffWarning, "warning dialog must not appear when there is nothing to analyze")
        XCTAssertNil(sut.pendingAIDiffText)
    }

    func testGenerateAIWithDiffOpensWarningDialog() async {
        let git = MockGitService()
        await git.setHeadDiffResult(.success("diff --git a/f b/f\n+line"))
        let sut = makeSUT(git: git)
        let project = makeProject()

        await sut.generateAICommitMessage(for: project)

        XCTAssertTrue(sut.showAIDiffWarning)
        XCTAssertEqual(sut.pendingAIDiffText, "diff --git a/f b/f\n+line")
        XCTAssertEqual(sut.pendingAIDiffProject?.id, project.id)
        XCTAssertNil(sut.commitPanelErrors[project.id])
    }

    // MARK: - sendAIDiff

    func testSendAIDiffSplitsSummaryAndDescription() async {
        let git = MockGitService()
        let aiService = MockAICommitService()
        await aiService.setGenerateResult(.success("feat: add feature\n\nThis is body line1\nbody line2"))
        let sut = makeSUT(git: git, aiService: aiService)
        let project = makeProject()

        await sut.sendAIDiff("some diff", for: project)

        XCTAssertEqual(sut.commitSummaries[project.id], "feat: add feature")
        XCTAssertEqual(sut.commitDescriptions[project.id], "This is body line1\nbody line2")
        XCTAssertFalse(sut.generatingAIProjects.contains(project.id), "in-progress flag must be released")
    }

    func testSendAIDiffTruncatesToMaxDiffLength() async {
        let git = MockGitService()
        let aiService = MockAICommitService()
        let sut = makeSUT(git: git, aiService: aiService)
        let project = makeProject()
        let oversized = String(repeating: "x", count: AIConstants.maxDiffLength + 1_000)

        await sut.sendAIDiff(oversized, for: project)

        let sentDiff = await aiService.lastDiff
        XCTAssertEqual(sentDiff?.count, AIConstants.maxDiffLength, "diff payload must be truncated before the API call")
    }
}

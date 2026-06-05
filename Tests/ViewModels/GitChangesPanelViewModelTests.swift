// MARK: - GitChangesPanelViewModelTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

@MainActor
final class GitChangesPanelViewModelTests: XCTestCase {

    func test_loadStats_noActiveProject_clearsStats() async {
        let git = MockGitService()
        let pm = MockProjectManager()
        let sut = GitChangesPanelViewModel(gitService: git, projectManager: pm)

        await sut.loadStats()
        XCTAssertTrue(sut.fileStats.isEmpty)
    }

    func test_loadStats_happyPath_populatesStats() async throws {
        let git = MockGitService()
        let stats: [String: GitDiffStat] = ["a.swift": GitDiffStat(added: 4, deleted: 2)]
        await git.setDiffStats(stats)

        let pm = MockProjectManager()
        let proj = try pm.addProject(at: URL(fileURLWithPath: "/tmp/proj"))
        pm.activeProjectId = proj.id

        let sut = GitChangesPanelViewModel(gitService: git, projectManager: pm)
        await sut.loadStats()
        XCTAssertEqual(sut.fileStats["a.swift"]?.added, 4)
    }

    func test_loadStats_gitError_resetsStats() async throws {
        let git = MockGitService()
        await git.setDiffStatsError()

        let pm = MockProjectManager()
        let proj = try pm.addProject(at: URL(fileURLWithPath: "/tmp/proj"))
        pm.activeProjectId = proj.id

        let sut = GitChangesPanelViewModel(gitService: git, projectManager: pm)
        sut.stageFile(GitFile(path: "x", status: .modified))  // populates Task
        await sut.loadStats()
        XCTAssertTrue(sut.fileStats.isEmpty)
    }

    func test_stageFile_invokesGitStage() async throws {
        let git = MockGitService()
        let pm = MockProjectManager()
        let proj = try pm.addProject(at: URL(fileURLWithPath: "/tmp/proj"))
        pm.activeProjectId = proj.id

        let sut = GitChangesPanelViewModel(gitService: git, projectManager: pm)
        sut.stageFile(GitFile(path: "x.swift", status: .modified))

        try await Task.sleep(for: .milliseconds(50))
        let count = await git.stageCallCount
        let last = await git.lastStagedFiles
        XCTAssertEqual(count, 1)
        XCTAssertEqual(last, ["x.swift"])
    }

    func test_unstageFile_invokesGitUnstage() async throws {
        let git = MockGitService()
        let pm = MockProjectManager()
        let proj = try pm.addProject(at: URL(fileURLWithPath: "/tmp/proj"))
        pm.activeProjectId = proj.id

        let sut = GitChangesPanelViewModel(gitService: git, projectManager: pm)
        sut.unstageFile(GitFile(path: "y.swift", status: .added))

        try await Task.sleep(for: .milliseconds(50))
        let count = await git.unstageCallCount
        XCTAssertEqual(count, 1)
    }
}

private extension MockGitService {
    func setDiffStats(_ stats: [String: GitDiffStat]) {
        diffStatsResult = .success(stats)
    }
    func setDiffStatsError() {
        diffStatsResult = .failure(GitServiceError.commandFailed(command: "diff", exitCode: 1, stderr: "boom"))
    }
}

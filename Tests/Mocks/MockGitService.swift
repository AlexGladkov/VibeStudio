import Foundation
@testable import VibeStudio

/// Mock implementation of ``GitServicing`` for unit tests.
///
/// Pre-configure `*Result` properties to control return values.
/// Check `*CallCount` properties to verify interactions.
actor MockGitService: GitServicing {

    // MARK: - Status & Info

    var statusResult: Result<GitStatus, Error> = .success(.empty)
    var statusCallCount = 0

    func status(at repository: URL) async throws -> GitStatus {
        statusCallCount += 1
        return try statusResult.get()
    }

    var diffResult: Result<[GitDiffHunk], Error> = .success([])
    var diffCallCount = 0

    func diff(file: String, staged: Bool, at repository: URL) async throws -> [GitDiffHunk] {
        diffCallCount += 1
        return try diffResult.get()
    }

    var fullStagedDiffResult: Result<String, Error> = .success("")
    var fullStagedDiffCallCount = 0

    func fullStagedDiff(at repository: URL) async throws -> String {
        fullStagedDiffCallCount += 1
        return try fullStagedDiffResult.get()
    }

    var headDiffResult: Result<String, Error> = .success("")
    var headDiffCallCount = 0

    func headDiff(at repository: URL) async throws -> String {
        headDiffCallCount += 1
        return try headDiffResult.get()
    }

    var branchesResult: Result<[GitBranch], Error> = .success([])
    var branchesCallCount = 0

    func branches(at repository: URL) async throws -> [GitBranch] {
        branchesCallCount += 1
        return try branchesResult.get()
    }

    var logResult: Result<[GitCommitInfo], Error> = .success([])
    var logCallCount = 0

    func log(limit: Int, at repository: URL) async throws -> [GitCommitInfo] {
        logCallCount += 1
        return try logResult.get()
    }

    // MARK: - Staging

    var stageCallCount = 0
    var lastStagedFiles: [String] = []

    func stage(files: [String], at repository: URL) async throws {
        stageCallCount += 1
        lastStagedFiles = files
    }

    var unstageCallCount = 0
    var lastUnstagedFiles: [String] = []

    func unstage(files: [String], at repository: URL) async throws {
        unstageCallCount += 1
        lastUnstagedFiles = files
    }

    // MARK: - Commit

    var commitResult: Result<String, Error> = .success("abc1234")
    var commitCallCount = 0
    var lastCommitMessage: String?

    @discardableResult
    func commit(message: String, at repository: URL) async throws -> String {
        commitCallCount += 1
        lastCommitMessage = message
        return try commitResult.get()
    }

    // MARK: - Remote

    var pushCallCount = 0

    func push(remote: String, at repository: URL) async throws {
        pushCallCount += 1
    }

    var pullCallCount = 0

    func pull(remote: String, at repository: URL) async throws {
        pullCallCount += 1
    }

    var fetchCallCount = 0

    func fetch(remote: String, at repository: URL) async throws {
        fetchCallCount += 1
    }

    var pushBranchCallCount = 0

    func pushBranch(_ branch: String, remote: String, at repository: URL) async throws {
        pushBranchCallCount += 1
    }

    var pullBranchCallCount = 0

    func pullBranch(_ branch: String, isCurrent: Bool, remote: String, at repository: URL) async throws {
        pullBranchCallCount += 1
    }

    var defaultRemoteResult = "origin"

    func defaultRemote(for branch: String?, at repository: URL) async -> String {
        defaultRemoteResult
    }

    // MARK: - Branches

    var checkoutCallCount = 0
    var lastCheckedOutBranch: String?

    func checkout(branch: String, at repository: URL) async throws {
        checkoutCallCount += 1
        lastCheckedOutBranch = branch
    }

    var createBranchCallCount = 0

    func createBranch(name: String, from startPoint: String?, at repository: URL) async throws {
        createBranchCallCount += 1
    }

    // MARK: - Utility

    var isRepositoryResult = true

    func isRepository(at path: URL) async -> Bool {
        isRepositoryResult
    }

    var repositoryRootResult: Result<URL, Error> = .success(URL(fileURLWithPath: "/tmp/repo"))

    func repositoryRoot(for path: URL) async throws -> URL {
        try repositoryRootResult.get()
    }

    func initRepository(at path: URL) async throws {
        // No-op in mock
    }

    var addRemoteCallCount = 0

    func addRemote(name: String, url: String, at repository: URL) async throws {
        addRemoteCallCount += 1
    }

    var remoteURLResult: String? = "https://github.com/user/repo.git"

    func remoteURL(name: String, at repository: URL) async -> String? {
        remoteURLResult
    }

    // MARK: - Ahead/Behind

    var aheadBehindResult: Result<(ahead: Int, behind: Int), Error> = .success((0, 0))

    func aheadBehind(at repository: URL) async throws -> (ahead: Int, behind: Int) {
        try aheadBehindResult.get()
    }
}

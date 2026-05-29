package studio.vibe.shared.testutil

import studio.vibe.shared.contract.GitServicing
import studio.vibe.shared.model.*

/**
 * Minimal [GitServicing] test double for [GitStatusPollerImpl] tests.
 *
 * Every call is configurable via [statusResult] / [aheadBehindResult].
 * Calls can be made to throw by setting [throwOnStatus].
 */
class FakeGitService : GitServicing {

    var statusResult: GitStatus = GitStatus.EMPTY
    var aheadBehindResult: Pair<Int, Int> = Pair(0, 0)
    var throwOnStatus: Throwable? = null

    var statusCallCount = 0
    var aheadBehindCallCount = 0

    override suspend fun status(at: FilePath): GitStatus {
        statusCallCount++
        throwOnStatus?.let { throw it }
        return statusResult
    }

    override suspend fun aheadBehind(at: FilePath): Pair<Int, Int> {
        aheadBehindCallCount++
        return aheadBehindResult
    }

    // ── Unused stubs ─────────────────────────────────────────────────────────

    override suspend fun diff(file: String, staged: Boolean, at: FilePath): List<GitDiffHunk> = emptyList()
    override suspend fun fullStagedDiff(at: FilePath): String = ""
    override suspend fun headDiff(at: FilePath): String = ""
    override suspend fun log(limit: Int, at: FilePath): List<GitCommitInfo> = emptyList()
    override suspend fun diffStats(at: FilePath): Map<String, GitDiffStat> = emptyMap()
    override suspend fun stage(files: List<String>, at: FilePath) {}
    override suspend fun unstage(files: List<String>, at: FilePath) {}
    override suspend fun commit(message: String, at: FilePath): String = "abc1234"
    override suspend fun push(remote: String, at: FilePath) {}
    override suspend fun pull(remote: String, at: FilePath) {}
    override suspend fun fetch(remote: String, at: FilePath) {}
    override suspend fun pushBranch(branch: String, remote: String, at: FilePath) {}
    override suspend fun pullBranch(branch: String, isCurrent: Boolean, remote: String, at: FilePath) {}
    override suspend fun addRemote(name: String, url: String, at: FilePath) {}
    override suspend fun remoteURL(name: String, at: FilePath): String? = null
    override suspend fun defaultRemote(forBranch: String?, at: FilePath): String = "origin"
    override suspend fun branches(at: FilePath): List<GitBranch> = emptyList()
    override suspend fun checkout(branch: String, at: FilePath) {}
    override suspend fun createBranch(name: String, from: String?, at: FilePath) {}
    override suspend fun deleteBranch(name: String, force: Boolean, at: FilePath) {}
    override suspend fun isRepository(at: FilePath): Boolean = true
    override suspend fun repositoryRoot(forPath: FilePath): FilePath = forPath
    override suspend fun initRepository(at: FilePath) {}

    fun reset() {
        statusResult = GitStatus.EMPTY
        aheadBehindResult = Pair(0, 0)
        throwOnStatus = null
        statusCallCount = 0
        aheadBehindCallCount = 0
    }
}

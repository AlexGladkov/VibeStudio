package studio.vibe.shared.testutil

import studio.vibe.shared.contract.AheadBehind
import studio.vibe.shared.contract.GitServicing
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.GitBranch
import studio.vibe.shared.model.GitCommitInfo
import studio.vibe.shared.model.GitDiffHunk
import studio.vibe.shared.model.GitDiffStat
import studio.vibe.shared.model.GitStatus

/**
 * Open base for all [GitServicing] test doubles.
 *
 * All methods have safe no-op / empty-return defaults.
 * Subclasses (including anonymous objects) override only the operations
 * they need to exercise.
 */
open class FakeGitServicingBase : GitServicing {
    override suspend fun status(at: FilePath): GitStatus = GitStatus.EMPTY
    override suspend fun diff(file: String, staged: Boolean, at: FilePath): List<GitDiffHunk> = emptyList()
    override suspend fun fullStagedDiff(at: FilePath): String = ""
    override suspend fun headDiff(at: FilePath): String = ""
    override suspend fun log(limit: Int, at: FilePath): List<GitCommitInfo> = emptyList()
    override suspend fun aheadBehind(at: FilePath): AheadBehind = AheadBehind(0, 0)
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
}

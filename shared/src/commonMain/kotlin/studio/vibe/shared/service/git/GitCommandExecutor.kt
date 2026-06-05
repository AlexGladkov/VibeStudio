package studio.vibe.shared.service.git

import studio.vibe.shared.contract.AheadBehind
import studio.vibe.shared.contract.GitServicing
import studio.vibe.shared.contract.ProcessRunner
import studio.vibe.shared.model.*
import studio.vibe.shared.service.git.executor.GitBranchExecutor
import studio.vibe.shared.service.git.executor.GitCommitExecutor
import studio.vibe.shared.service.git.executor.GitInspectExecutor
import studio.vibe.shared.service.git.executor.GitProcessRunner
import studio.vibe.shared.service.git.executor.GitRemoteExecutor
import studio.vibe.shared.service.git.executor.GitStagingExecutor
import studio.vibe.shared.service.git.executor.GitStatusExecutor

/**
 * Thin facade implementing [GitServicing] by delegating to six focused executors.
 *
 * All git commands are executed via argument arrays — never shell string interpolation —
 * preventing command injection. Branch names are validated before use. File paths are
 * always preceded by `--` to prevent option injection.
 *
 * @param processRunner Platform-specific process execution abstraction.
 * @param gitPath Path to the git binary (default: "git", resolved from PATH).
 */
class GitCommandExecutor(
    processRunner: ProcessRunner,
    gitPath: String = "git",
) : GitServicing {

    private val runner = GitProcessRunner(processRunner, gitPath)

    private val statusExecutor = GitStatusExecutor(runner)
    private val stagingExecutor = GitStagingExecutor(runner)
    private val commitExecutor = GitCommitExecutor(runner)
    private val remoteExecutor = GitRemoteExecutor(runner)
    private val branchExecutor = GitBranchExecutor(runner)
    private val inspectExecutor = GitInspectExecutor(runner)

    // ── GitStatusQuerying ──────────────────────────────────────────────────────

    override suspend fun status(at: FilePath): GitStatus =
        statusExecutor.status(at)

    override suspend fun diff(file: String, staged: Boolean, at: FilePath): List<GitDiffHunk> =
        statusExecutor.diff(file, staged, at)

    override suspend fun fullStagedDiff(at: FilePath): String =
        statusExecutor.fullStagedDiff(at)

    override suspend fun headDiff(at: FilePath): String =
        statusExecutor.headDiff(at)

    override suspend fun diffStats(at: FilePath): Map<String, GitDiffStat> =
        statusExecutor.diffStats(at)

    override suspend fun log(limit: Int, at: FilePath): List<GitCommitInfo> =
        statusExecutor.log(limit, at)

    override suspend fun aheadBehind(at: FilePath): AheadBehind =
        statusExecutor.aheadBehind(at)

    // ── GitStaging ─────────────────────────────────────────────────────────────

    override suspend fun stage(files: List<String>, at: FilePath) =
        stagingExecutor.stage(files, at)

    override suspend fun unstage(files: List<String>, at: FilePath) =
        stagingExecutor.unstage(files, at)

    // ── GitCommitting ──────────────────────────────────────────────────────────

    override suspend fun commit(message: String, at: FilePath): String =
        commitExecutor.commit(message, at)

    // ── GitRemoteOperating ─────────────────────────────────────────────────────

    override suspend fun push(remote: String, at: FilePath) =
        remoteExecutor.push(remote, at)

    override suspend fun pull(remote: String, at: FilePath) =
        remoteExecutor.pull(remote, at)

    override suspend fun fetch(remote: String, at: FilePath) =
        remoteExecutor.fetch(remote, at)

    override suspend fun pushBranch(branch: String, remote: String, at: FilePath) =
        remoteExecutor.pushBranch(branch, remote, at)

    override suspend fun pullBranch(branch: String, isCurrent: Boolean, remote: String, at: FilePath) =
        remoteExecutor.pullBranch(branch, isCurrent, remote, at)

    override suspend fun addRemote(name: String, url: String, at: FilePath) =
        remoteExecutor.addRemote(name, url, at)

    override suspend fun remoteURL(name: String, at: FilePath): String? =
        remoteExecutor.remoteURL(name, at)

    override suspend fun defaultRemote(forBranch: String?, at: FilePath): String =
        remoteExecutor.defaultRemote(forBranch, at)

    // ── GitBranching ───────────────────────────────────────────────────────────

    override suspend fun branches(at: FilePath): List<GitBranch> =
        branchExecutor.branches(at)

    override suspend fun checkout(branch: String, at: FilePath) =
        branchExecutor.checkout(branch, at)

    override suspend fun createBranch(name: String, from: String?, at: FilePath) =
        branchExecutor.createBranch(name, from, at)

    override suspend fun deleteBranch(name: String, force: Boolean, at: FilePath) =
        branchExecutor.deleteBranch(name, force, at)

    // ── GitRepositoryInspection ────────────────────────────────────────────────

    override suspend fun isRepository(at: FilePath): Boolean =
        inspectExecutor.isRepository(at)

    override suspend fun repositoryRoot(forPath: FilePath): FilePath =
        inspectExecutor.repositoryRoot(forPath)

    override suspend fun initRepository(at: FilePath) =
        inspectExecutor.initRepository(at)
}

package studio.vibe.shared.contract

import kotlin.coroutines.cancellation.CancellationException
import studio.vibe.shared.model.*
import kotlin.time.Duration

/** Result of comparing local and upstream branch commit counts. */
data class AheadBehind(val ahead: Int, val behind: Int)

// Git Status Querying
interface GitStatusQuerying {
    @Throws(GitServiceError::class, CancellationException::class)
    suspend fun status(at: FilePath): GitStatus

    @Throws(GitServiceError::class, CancellationException::class)
    suspend fun diff(file: String, staged: Boolean, at: FilePath): List<GitDiffHunk>

    @Throws(GitServiceError::class, CancellationException::class)
    suspend fun fullStagedDiff(at: FilePath): String

    @Throws(GitServiceError::class, CancellationException::class)
    suspend fun headDiff(at: FilePath): String

    @Throws(GitServiceError::class, CancellationException::class)
    suspend fun log(limit: Int = 50, at: FilePath): List<GitCommitInfo>

    @Throws(GitServiceError::class, CancellationException::class)
    suspend fun aheadBehind(at: FilePath): AheadBehind

    @Throws(GitServiceError::class, CancellationException::class)
    suspend fun diffStats(at: FilePath): Map<String, GitDiffStat>
}

// Git Staging
interface GitStaging {
    @Throws(GitServiceError::class, CancellationException::class)
    suspend fun stage(files: List<String>, at: FilePath)

    @Throws(GitServiceError::class, CancellationException::class)
    suspend fun unstage(files: List<String>, at: FilePath)
}

// Git Committing
interface GitCommitting {
    @Throws(GitServiceError::class, CancellationException::class)
    suspend fun commit(message: String, at: FilePath): String
}

// Git Remote Operating
interface GitRemoteOperating {
    @Throws(GitServiceError::class, CancellationException::class)
    suspend fun push(remote: String = "origin", at: FilePath)

    @Throws(GitServiceError::class, CancellationException::class)
    suspend fun pull(remote: String = "origin", at: FilePath)

    @Throws(GitServiceError::class, CancellationException::class)
    suspend fun fetch(remote: String = "origin", at: FilePath)

    @Throws(GitServiceError::class, CancellationException::class)
    suspend fun pushBranch(branch: String, remote: String = "origin", at: FilePath)

    @Throws(GitServiceError::class, CancellationException::class)
    suspend fun pullBranch(branch: String, isCurrent: Boolean, remote: String = "origin", at: FilePath)

    @Throws(GitServiceError::class, CancellationException::class)
    suspend fun addRemote(name: String, url: String, at: FilePath)

    @Throws(GitServiceError::class, CancellationException::class)
    suspend fun remoteURL(name: String = "origin", at: FilePath): String?

    @Throws(GitServiceError::class, CancellationException::class)
    suspend fun defaultRemote(forBranch: String?, at: FilePath): String
}

// Git Branching
interface GitBranching {
    @Throws(GitServiceError::class, CancellationException::class)
    suspend fun branches(at: FilePath): List<GitBranch>

    @Throws(GitServiceError::class, CancellationException::class)
    suspend fun checkout(branch: String, at: FilePath)

    @Throws(GitServiceError::class, CancellationException::class)
    suspend fun createBranch(name: String, from: String? = null, at: FilePath)

    /**
     * Delete a local branch.
     *
     * @param name Branch name to delete.
     * @param force When true, uses `git branch -D` (force-delete even if unmerged).
     *   When false, uses `git branch -d` (safe-delete — fails if unmerged).
     * @param at Repository root path.
     * @throws GitServiceError if the branch does not exist, is currently checked out,
     *   or (when [force] is false) has unmerged commits.
     */
    @Throws(GitServiceError::class, CancellationException::class)
    suspend fun deleteBranch(name: String, force: Boolean = false, at: FilePath)
}

// Git Repository Inspection
interface GitRepositoryInspection {
    @Throws(GitServiceError::class, CancellationException::class)
    suspend fun isRepository(at: FilePath): Boolean

    @Throws(GitServiceError::class, CancellationException::class)
    suspend fun repositoryRoot(forPath: FilePath): FilePath

    @Throws(GitServiceError::class, CancellationException::class)
    suspend fun initRepository(at: FilePath)
}

// Unified Git Service
interface GitServicing : GitStatusQuerying, GitStaging, GitCommitting,
    GitRemoteOperating, GitBranching, GitRepositoryInspection

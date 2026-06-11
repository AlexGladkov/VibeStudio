package studio.vibe.shared.feature.git.domain.contract

import kotlin.coroutines.cancellation.CancellationException
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.feature.git.domain.model.GitBranch
import studio.vibe.shared.feature.git.domain.model.GitServiceError

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

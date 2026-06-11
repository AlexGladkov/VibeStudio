package studio.vibe.shared.feature.git.domain.contract

import kotlin.coroutines.cancellation.CancellationException
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.feature.git.domain.model.GitCommitInfo
import studio.vibe.shared.feature.git.domain.model.GitDiffHunk
import studio.vibe.shared.feature.git.domain.model.GitDiffStat
import studio.vibe.shared.feature.git.domain.model.GitServiceError
import studio.vibe.shared.feature.git.domain.model.AheadBehind
import studio.vibe.shared.core.common.git.GitStatus

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

package studio.vibe.shared.feature.git.domain.contract

import kotlin.coroutines.cancellation.CancellationException
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.feature.git.domain.model.GitServiceError

interface GitRepositoryInspection {
    @Throws(GitServiceError::class, CancellationException::class)
    suspend fun isRepository(at: FilePath): Boolean

    @Throws(GitServiceError::class, CancellationException::class)
    suspend fun repositoryRoot(forPath: FilePath): FilePath

    @Throws(GitServiceError::class, CancellationException::class)
    suspend fun initRepository(at: FilePath)
}

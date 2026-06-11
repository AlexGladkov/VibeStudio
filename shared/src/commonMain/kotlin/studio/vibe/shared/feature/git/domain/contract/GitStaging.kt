package studio.vibe.shared.feature.git.domain.contract

import kotlin.coroutines.cancellation.CancellationException
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.feature.git.domain.model.GitServiceError

interface GitStaging {
    @Throws(GitServiceError::class, CancellationException::class)
    suspend fun stage(files: List<String>, at: FilePath)

    @Throws(GitServiceError::class, CancellationException::class)
    suspend fun unstage(files: List<String>, at: FilePath)
}

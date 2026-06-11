package studio.vibe.shared.feature.git.domain.contract

import kotlin.coroutines.cancellation.CancellationException
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.feature.git.domain.model.GitServiceError

interface GitCommitting {
    @Throws(GitServiceError::class, CancellationException::class)
    suspend fun commit(message: String, at: FilePath): String
}

package studio.vibe.shared.feature.git.domain.contract

import kotlin.coroutines.cancellation.CancellationException
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.feature.git.domain.model.GitServiceError

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

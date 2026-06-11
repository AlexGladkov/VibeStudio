package studio.vibe.shared.feature.git.domain.contract

import kotlinx.coroutines.flow.StateFlow
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.core.common.git.GitStatus

interface GitStatusPolling {
    val status: StateFlow<GitStatus>
    val isPolling: StateFlow<Boolean>
    val lastError: StateFlow<Throwable?>

    fun startPolling(repository: FilePath, isActive: Boolean)
    fun stopPolling()
    fun refreshNow()
}

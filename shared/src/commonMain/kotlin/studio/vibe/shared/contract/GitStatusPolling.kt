package studio.vibe.shared.contract

import kotlinx.coroutines.flow.StateFlow
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.GitStatus

interface GitStatusPolling {
    val status: StateFlow<GitStatus>
    val isPolling: StateFlow<Boolean>
    val lastError: StateFlow<Throwable?>

    fun startPolling(repository: FilePath, isActive: Boolean)
    fun stopPolling()
    fun refreshNow()
}

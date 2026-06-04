package studio.vibe.shared.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import studio.vibe.shared.contract.GitRemoteOperating
import studio.vibe.shared.model.FilePath

data class GitRemoteSetupState(
    val remoteName: String = "origin",
    val remoteURL: String = "",
    val existingRemoteURL: String? = null,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
)

class GitRemoteSetupViewModel(
    private val gitService: GitRemoteOperating,
    parentScope: CoroutineScope,
) : BaseViewModel(parentScope) {

    private val _state = MutableStateFlow(GitRemoteSetupState())
    val state: StateFlow<GitRemoteSetupState> = _state.asStateFlow()

    fun loadExistingRemote(path: FilePath) {
        scope.launch {
            _state.update { it.copy(isLoading = true) }
            runCatching {
                val url = gitService.remoteURL(name = "origin", at = path)
                _state.update { s ->
                    s.copy(
                        existingRemoteURL = url,
                        remoteURL = url ?: s.remoteURL,
                        isLoading = false,
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun updateRemoteName(name: String) {
        _state.update { it.copy(remoteName = name, errorMessage = null) }
    }

    fun updateRemoteURL(url: String) {
        _state.update { it.copy(remoteURL = url, errorMessage = null) }
    }

    fun addRemote(path: FilePath) {
        val name = _state.value.remoteName.trim()
        val url = _state.value.remoteURL.trim()
        if (name.isBlank()) {
            _state.update { it.copy(errorMessage = "Remote name cannot be empty") }
            return
        }
        if (url.isBlank()) {
            _state.update { it.copy(errorMessage = "Remote URL cannot be empty") }
            return
        }
        scope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                gitService.addRemote(name = name, url = url, at = path)
                _state.update { it.copy(isLoading = false, isSaved = true, existingRemoteURL = url) }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun reset() {
        _state.update { GitRemoteSetupState() }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}

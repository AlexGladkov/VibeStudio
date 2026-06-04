package studio.vibe.shared.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import studio.vibe.shared.contract.PersistenceStore
import studio.vibe.shared.model.FilePath

data class SpecEditorState(
    val content: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    val errorMessage: String? = null,
    val currentPath: FilePath? = null,
)

class SpecEditorViewModel(
    private val persistenceStore: PersistenceStore,
    parentScope: CoroutineScope,
) : BaseViewModel(parentScope) {

    private val _state = MutableStateFlow(SpecEditorState())
    val state: StateFlow<SpecEditorState> = _state.asStateFlow()

    fun loadSpec(path: FilePath) {
        scope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null, currentPath = path) }
            runCatching {
                val bytes = persistenceStore.readFile(path)
                val content = bytes?.decodeToString() ?: ""
                _state.update { it.copy(content = content, isLoading = false, isDirty = false) }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun updateContent(content: String) {
        _state.update { it.copy(content = content, isDirty = true) }
    }

    fun saveSpec() {
        val path = _state.value.currentPath ?: return
        scope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching {
                persistenceStore.writeFile(path, _state.value.content.encodeToByteArray())
                _state.update { it.copy(isSaving = false, isDirty = false) }
            }.onFailure { e ->
                _state.update { it.copy(isSaving = false, errorMessage = e.message) }
            }
        }
    }

    fun saveSpec(path: FilePath) {
        scope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null, currentPath = path) }
            runCatching {
                persistenceStore.writeFile(path, _state.value.content.encodeToByteArray())
                _state.update { it.copy(isSaving = false, isDirty = false) }
            }.onFailure { e ->
                _state.update { it.copy(isSaving = false, errorMessage = e.message) }
            }
        }
    }

    fun clearContent() {
        _state.update { SpecEditorState() }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}

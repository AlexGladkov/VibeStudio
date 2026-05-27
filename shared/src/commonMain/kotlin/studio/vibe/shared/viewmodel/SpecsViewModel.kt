package studio.vibe.shared.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import studio.vibe.shared.contract.PersistenceStore
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.SpecFile
import studio.vibe.shared.model.SpecStatus
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
data class SpecsState(
    val specs: List<SpecFile> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalUuidApi::class)
class SpecsViewModel(
    private val persistenceStore: PersistenceStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(SpecsState())
    val state: StateFlow<SpecsState> = _state.asStateFlow()

    fun loadSpecs(projectPath: FilePath) {
        scope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val specsDir = projectPath.child(".codespeak").child("specs")
                val specsDirExists = persistenceStore.isDirectory(specsDir)
                val specs = if (specsDirExists) {
                    scanSpecFiles(specsDir)
                } else {
                    // Try root-level specs directory
                    val rootSpecs = projectPath.child("specs")
                    if (persistenceStore.isDirectory(rootSpecs)) {
                        scanSpecFiles(rootSpecs)
                    } else {
                        emptyList()
                    }
                }
                _state.update { it.copy(specs = specs, isLoading = false) }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    private suspend fun scanSpecFiles(dir: FilePath): List<SpecFile> {
        val entries = runCatching { persistenceStore.listDirectory(dir) }.getOrElse { emptyList() }
        return entries
            .filter { it.path.endsWith(".md") || it.path.endsWith(".cs.md") }
            .map { path ->
                SpecFile(
                    path = path,
                    status = SpecStatus.UNKNOWN,
                )
            }
            .sortedBy { it.name }
    }

    fun refresh(projectPath: FilePath) {
        loadSpecs(projectPath)
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}

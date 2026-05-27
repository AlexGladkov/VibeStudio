package studio.vibe.shared.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.model.Project
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class ProjectSettingsState(
    val productionURL: String = "",
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalUuidApi::class)
class ProjectSettingsViewModel(
    private val projectManaging: ProjectManaging,
) {
    private val _state = MutableStateFlow(ProjectSettingsState())
    val state: StateFlow<ProjectSettingsState> = _state.asStateFlow()

    fun load(projectId: Uuid) {
        val project = projectManaging.project(projectId) ?: return
        _state.update { it.copy(productionURL = project.productionURL.orEmpty(), isSaved = false) }
    }

    fun updateProductionURL(url: String) {
        _state.update { it.copy(productionURL = url, isSaved = false) }
    }

    fun saveProductionURL(projectId: Uuid) {
        runCatching {
            projectManaging.updateProject(projectId) { project ->
                project.copy(productionURL = _state.value.productionURL.takeIf { it.isNotBlank() })
            }
            projectManaging.save()
            _state.update { it.copy(isSaved = true, errorMessage = null) }
        }.onFailure { e ->
            _state.update { it.copy(errorMessage = e.message, isSaved = false) }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}

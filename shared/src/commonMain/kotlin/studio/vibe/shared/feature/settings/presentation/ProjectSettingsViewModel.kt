package studio.vibe.shared.feature.settings.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import studio.vibe.shared.core.common.BaseViewModel
import studio.vibe.shared.core.common.project.ProjectManaging
import studio.vibe.shared.core.common.project.Project
import studio.vibe.shared.feature.settings.presentation.ProjectSettingsState
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ProjectSettingsViewModel(
    private val projectManaging: ProjectManaging,
    parentScope: CoroutineScope,
) : BaseViewModel(parentScope) {

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
        scope.launch {
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
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}

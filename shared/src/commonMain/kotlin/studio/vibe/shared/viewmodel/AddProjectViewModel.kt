package studio.vibe.shared.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.Project
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
data class AddProjectState(
    val recentProjects: List<Project> = emptyList(),
    val errorMessage: String? = null,
)

@OptIn(ExperimentalUuidApi::class)
class AddProjectViewModel(
    private val projectManaging: ProjectManaging,
) {
    private val _state = MutableStateFlow(AddProjectState())
    val state: StateFlow<AddProjectState> = _state.asStateFlow()

    init {
        loadRecentProjects()
    }

    fun loadRecentProjects() {
        _state.update { it.copy(recentProjects = projectManaging.recentProjects.value) }
    }

    fun openRecentProject(path: FilePath): Project? {
        return runCatching {
            val existing = projectManaging.project(path)
            if (existing != null) {
                projectManaging.setActiveProjectId(existing.id)
                _state.update { it.copy(errorMessage = null) }
                existing
            } else {
                val project = projectManaging.addProject(path)
                projectManaging.setActiveProjectId(project.id)
                _state.update { it.copy(errorMessage = null) }
                project
            }
        }.onFailure { e ->
            _state.update { it.copy(errorMessage = e.message) }
        }.getOrNull()
    }

    fun addProject(path: FilePath): Project? {
        return runCatching {
            val project = projectManaging.addProject(path)
            projectManaging.setActiveProjectId(project.id)
            _state.update { it.copy(errorMessage = null) }
            project
        }.onFailure { e ->
            _state.update { it.copy(errorMessage = e.message) }
        }.getOrNull()
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}

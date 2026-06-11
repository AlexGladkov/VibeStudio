package studio.vibe.shared.feature.project.presentation

import kotlinx.coroutines.CoroutineScope
import studio.vibe.shared.core.common.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import studio.vibe.shared.core.common.project.ProjectManaging
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.core.common.project.Project
import kotlin.uuid.ExperimentalUuidApi

/**
 * ViewModel for the "Add project" popover + recent projects list.
 *
 * Exposes a single immutable [AddProjectState] via [state] (StateFlow).  Views
 * issue intents through [openRecentProject] / [addProject]; the resulting
 * [Project] is published in `state.openedProject` and must be consumed via
 * [consumeOpened] before issuing the next intent.
 *
 * @param parentScope Parent scope from the DI container or Compose host.
 *                    The VM creates a child scope that can be cancelled independently
 *                    via [dispose]. Inject a [TestScope] from unit tests.
 */
@OptIn(ExperimentalUuidApi::class)
class AddProjectViewModel(
    private val projectManaging: ProjectManaging,
    parentScope: CoroutineScope,
) : BaseViewModel(parentScope) {

    private val _state = MutableStateFlow(AddProjectState())
    val state: StateFlow<AddProjectState> = _state.asStateFlow()

    init {
        loadRecentProjects()
    }

    fun loadRecentProjects() {
        _state.update { it.copy(recentProjects = projectManaging.recentProjects.value) }
    }

    /** Opens an existing recent project (registering it if it has been removed). */
    fun openRecentProject(path: FilePath) {
        scope.launch {
            runCatching {
                val existing = projectManaging.project(path)
                val project = if (existing != null) {
                    projectManaging.setActiveProjectId(existing.id)
                    existing
                } else {
                    val created = projectManaging.addProject(path)
                    projectManaging.setActiveProjectId(created.id)
                    created
                }
                _state.update { it.copy(errorMessage = null, openedProject = project) }
            }.onFailure { e ->
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    /** Registers a new project and activates it. */
    fun addProject(path: FilePath) {
        scope.launch {
            runCatching {
                val project = projectManaging.addProject(path)
                projectManaging.setActiveProjectId(project.id)
                _state.update { it.copy(errorMessage = null, openedProject = project) }
            }.onFailure { e ->
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    /** Acknowledge the last opened project so subsequent intents can be tracked. */
    fun consumeOpened() {
        _state.update { it.copy(openedProject = null) }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}

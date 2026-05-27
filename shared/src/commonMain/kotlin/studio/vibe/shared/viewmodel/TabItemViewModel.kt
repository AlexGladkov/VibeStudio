package studio.vibe.shared.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.contract.TerminalSessionManaging
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class TabItemState(
    val isCloseConfirmationVisible: Boolean = false,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalUuidApi::class)
class TabItemViewModel(
    private val projectManaging: ProjectManaging,
    private val terminalSessionManaging: TerminalSessionManaging,
) {
    private val _state = MutableStateFlow(TabItemState())
    val state: StateFlow<TabItemState> = _state.asStateFlow()

    fun closeProject(projectId: Uuid) {
        runCatching {
            // Kill all terminal sessions for the project
            terminalSessionManaging.killAllSessions(projectId)

            // Determine the next active project before removing
            val projects = projectManaging.projects.value
            val currentIndex = projects.indexOfFirst { it.id == projectId }
            val nextProject = when {
                projects.size <= 1 -> null
                currentIndex > 0 -> projects[currentIndex - 1]
                else -> projects.getOrNull(currentIndex + 1)
            }

            projectManaging.removeProject(projectId)

            if (nextProject != null) {
                projectManaging.setActiveProjectId(nextProject.id)
            } else {
                projectManaging.setActiveProjectId(null)
            }

            _state.update { it.copy(isCloseConfirmationVisible = false, errorMessage = null) }
        }.onFailure { e ->
            _state.update { it.copy(errorMessage = e.message) }
        }
    }

    fun showCloseConfirmation() {
        _state.update { it.copy(isCloseConfirmationVisible = true) }
    }

    fun hideCloseConfirmation() {
        _state.update { it.copy(isCloseConfirmationVisible = false) }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}

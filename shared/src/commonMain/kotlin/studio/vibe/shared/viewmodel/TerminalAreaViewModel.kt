package studio.vibe.shared.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.contract.TerminalSessionManaging
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.TerminalSession
import studio.vibe.shared.model.TerminalSize
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class TerminalAreaState(
    val sessions: List<TerminalSession> = emptyList(),
    val activeSessionId: Uuid? = null,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalUuidApi::class)
class TerminalAreaViewModel(
    private val projectManaging: ProjectManaging,
    private val terminalSessionManaging: TerminalSessionManaging,
) {
    private val _state = MutableStateFlow(TerminalAreaState())
    val state: StateFlow<TerminalAreaState> = _state.asStateFlow()

    fun createSession(projectId: Uuid, size: TerminalSize = TerminalSize(columns = 80, rows = 24)) {
        val project = projectManaging.project(projectId) ?: run {
            _state.update { it.copy(errorMessage = "Project not found") }
            return
        }
        runCatching {
            val session = terminalSessionManaging.createSession(
                projectId = projectId,
                shell = project.shellPath,
                workingDirectory = project.path,
                size = size,
            )
            _state.update { s ->
                s.copy(
                    sessions = s.sessions + session,
                    activeSessionId = session.id,
                    errorMessage = null,
                )
            }
        }.onFailure { e ->
            _state.update { it.copy(errorMessage = e.message) }
        }
    }

    fun activateSession(sessionId: Uuid) {
        _state.update { it.copy(activeSessionId = sessionId) }
    }

    fun refreshSessions(projectId: Uuid) {
        val sessions = terminalSessionManaging.sessions(projectId)
        _state.update { s ->
            val activeId = if (s.activeSessionId in sessions.map { it.id }) s.activeSessionId
                else sessions.firstOrNull()?.id
            s.copy(sessions = sessions, activeSessionId = activeId)
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}

package studio.vibe.shared.feature.terminal.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import studio.vibe.shared.core.common.BaseViewModel
import studio.vibe.shared.core.common.project.ProjectManaging
import studio.vibe.shared.core.common.terminal.TerminalSession
import studio.vibe.shared.core.common.terminal.TerminalSize
import studio.vibe.shared.feature.terminal.domain.usecase.CreateTerminalSessionParams
import studio.vibe.shared.feature.terminal.domain.usecase.CreateTerminalSessionUseCase
import studio.vibe.shared.feature.terminal.domain.usecase.ListTerminalSessionsParams
import studio.vibe.shared.feature.terminal.domain.usecase.ListTerminalSessionsUseCase
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class TerminalAreaViewModel(
    private val projectManaging: ProjectManaging,
    private val createTerminalSessionUseCase: CreateTerminalSessionUseCase,
    private val listTerminalSessionsUseCase: ListTerminalSessionsUseCase,
    parentScope: CoroutineScope,
) : BaseViewModel(parentScope) {
    private val _state = MutableStateFlow(TerminalAreaState())
    val state: StateFlow<TerminalAreaState> = _state.asStateFlow()

    fun createSession(projectId: Uuid, size: TerminalSize = TerminalSize(columns = 80, rows = 24)) {
        val project = projectManaging.project(projectId) ?: run {
            _state.update { it.copy(errorMessage = "Project not found") }
            return
        }
        scope.launch {
            val result = createTerminalSessionUseCase(
                CreateTerminalSessionParams(
                    projectId = projectId,
                    shell = project.shellPath,
                    workingDirectory = project.path,
                    size = size,
                )
            )
            result.fold(
                onSuccess = { session ->
                    _state.update { s ->
                        s.copy(
                            sessions = s.sessions + session,
                            activeSessionId = session.id,
                            errorMessage = null,
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(errorMessage = e.message) }
                },
            )
        }
    }

    fun activateSession(sessionId: Uuid) {
        _state.update { it.copy(activeSessionId = sessionId) }
    }

    fun refreshSessions(projectId: Uuid) {
        scope.launch {
            val sessions = listTerminalSessionsUseCase(ListTerminalSessionsParams(projectId))
                .getOrDefault(emptyList())
            _state.update { s ->
                val activeId = if (s.activeSessionId in sessions.map { it.id }) s.activeSessionId
                    else sessions.firstOrNull()?.id
                s.copy(sessions = sessions, activeSessionId = activeId)
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}

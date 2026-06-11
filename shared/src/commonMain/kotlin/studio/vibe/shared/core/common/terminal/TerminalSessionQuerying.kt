package studio.vibe.shared.core.common.terminal

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import studio.vibe.shared.core.common.terminal.TabActivityState
import studio.vibe.shared.core.common.terminal.TerminalSession
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
interface TerminalSessionQuerying {
    val sessionsByProject: StateFlow<Map<Uuid, List<TerminalSession>>>
    val projectActivityStates: StateFlow<Map<Uuid, TabActivityState>>
    fun session(id: Uuid): TerminalSession?
    fun sessions(projectId: Uuid): List<TerminalSession>
    val sessionEvents: Flow<TerminalSessionEvent>
    fun markProjectSeen(projectId: Uuid)
}

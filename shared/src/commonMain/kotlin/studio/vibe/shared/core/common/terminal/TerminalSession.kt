package studio.vibe.shared.core.common.terminal

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class TerminalSession(
    val id: Uuid = Uuid.random(),
    val projectId: Uuid,
    val title: String = "zsh",
    val state: TerminalSessionState = TerminalSessionState.Running,
    val splitDirection: SplitDirection? = null,
    val isAgentSession: Boolean = false,
)

package studio.vibe.shared.model

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

enum class TabActivityState {
    IDLE, RUNNING, WAITING_FOR_INPUT, ERROR, HIDDEN
}

@OptIn(ExperimentalUuidApi::class)
data class TerminalSession(
    val id: Uuid = Uuid.random(),
    val projectId: Uuid,
    val title: String = "zsh",
    val state: TerminalSessionState = TerminalSessionState.Running,
    val splitDirection: SplitDirection? = null,
    val isAgentSession: Boolean = false,
)

sealed class TerminalSessionState {
    data object Running : TerminalSessionState()
    data object HasActivity : TerminalSessionState()
    data class Exited(val code: Int) : TerminalSessionState()
}

@Serializable
enum class SplitDirection {
    horizontal, vertical
}

data class TerminalSize(
    val columns: Int,
    val rows: Int,
)

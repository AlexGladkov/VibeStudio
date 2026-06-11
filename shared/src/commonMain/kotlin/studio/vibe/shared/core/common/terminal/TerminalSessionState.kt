package studio.vibe.shared.core.common.terminal

sealed class TerminalSessionState {
    data object Running : TerminalSessionState()
    data object HasActivity : TerminalSessionState()
    data class Exited(val code: Int) : TerminalSessionState()
}

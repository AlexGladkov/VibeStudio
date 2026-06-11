package studio.vibe.shared.core.common

public sealed class AgentExitSequence {
    public data object CtrlC : AgentExitSequence()
    public data class CtrlCThenCommand(val command: String) : AgentExitSequence()
}

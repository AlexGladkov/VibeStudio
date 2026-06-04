package studio.vibe.shared.model

public sealed class AgentExitSequence {
    public data object CtrlC : AgentExitSequence()
    public data class CtrlCThenCommand(val command: String) : AgentExitSequence()
}

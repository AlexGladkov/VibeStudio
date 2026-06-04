package studio.vibe.shared.contract

import studio.vibe.shared.model.AgentExitSequence

/**
 * Pluggable description of an AI coding agent that can be selected from the
 * toolbar and launched as a PTY child process.
 *
 * ### Plugin architecture
 *
 * Built-in agents (Claude, OpenCode, Codex, Gemini, QwenCode, CodeSpeak) are
 * registered via [DefaultAIAgentRegistry][studio.vibe.shared.service.agent.DefaultAIAgentRegistry].
 * New agents — including third-party plugins — register themselves through
 * [AIAgentRegistry.register]; no modification of [ToolbarViewModel],
 * [AgentAvailabilityServiceImpl][studio.vibe.shared.service.agent.AgentAvailabilityServiceImpl]
 * or other consumers is required.
 */
interface AIAgent {

    /** Unique stable identifier (used for persistence + lookup). */
    val id: String

    val displayName: String
    val executableName: String

    val launchArguments: List<String> get() = emptyList()

    /** When `true`, the agent's `launchCommand` is sent via shell stdin instead of `exec`. */
    val launchViaShellInput: Boolean get() = false

    /** Newline-terminated command string sent to the PTY to start the agent. */
    val launchCommand: String

    val exitSequence: AgentExitSequence get() = AgentExitSequence.CtrlC

    /** Process-env variable that carries the API key, or `null` if the agent does not need one. */
    val apiKeyEnvironmentVariable: String?

    val installHint: String
    val shortDescription: String
    val prerequisite: String?
    val prerequisiteCheckCommand: String?
    val setupInstructions: String?
}

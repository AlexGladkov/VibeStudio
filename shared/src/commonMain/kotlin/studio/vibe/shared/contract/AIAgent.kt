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

    // ── Resume support ────────────────────────────────────────────────────────

    /**
     * Arguments to append to the launch command to resume a native session.
     *
     * Returns `null` when the agent does not support session resumption.
     *
     * Example (Claude): `listOf("--resume", nativeSessionId)`
     * Example (Codex): `listOf("resume", nativeSessionId)`
     */
    fun resumeArgsFor(nativeSessionId: String): List<String>? = null

    // ── JSON stream output ────────────────────────────────────────────────────

    /**
     * `true` when the agent can emit structured JSON on stdout, allowing
     * the desktop layer to extract the native session UUID automatically.
     *
     * Currently `true` for Claude and Codex.
     */
    val supportsJsonStreamOutput: Boolean get() = false

    /**
     * Arguments to append to the launch command to enable JSON stream output.
     * Meaningful only when [supportsJsonStreamOutput] is `true`.
     *
     * Claude:  `listOf("--output-format", "stream-json")`
     * Codex:   `listOf("--output-format", "stream-json")`
     */
    val jsonStreamOutputArgs: List<String>? get() = null
}

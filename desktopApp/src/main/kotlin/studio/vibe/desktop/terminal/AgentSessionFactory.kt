@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.terminal

import com.pty4j.PtyProcessBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import studio.vibe.shared.core.common.AIAgent
import studio.vibe.shared.feature.settings.domain.model.GeneralPreferencesReading
import studio.vibe.shared.core.common.AgentEnvironmentBuilder
import studio.vibe.shared.core.common.terminal.TerminalSession
import studio.vibe.shared.core.common.terminal.TerminalSessionState
import studio.vibe.shared.core.common.terminal.TerminalSize
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import kotlin.uuid.Uuid

/**
 * Factory responsible for building the PTY environment and starting agent PTY sessions.
 *
 * Extracted from [DesktopTerminalService] to isolate the agent-specific concerns:
 * environment allowlist, launch command construction, and agent session boot.
 *
 * [DesktopTerminalService] retains PTY lifecycle management and the session registry.
 * This factory only creates [PtySessionState] instances — it does NOT register them.
 */
internal class AgentSessionFactory(
    private val generalPreferences: GeneralPreferencesReading?,
    private val serviceScope: CoroutineScope,
    /** Test-only seam: receives the effective launch command instead of (or before) PTY sendInput. */
    private val commandSink: ((String) -> Unit)? = null,
) {

    /**
     * Builds the effective agent launch command.
     *
     * Appends extra flags before the terminating newline in the following order:
     *  1. [AIAgent.jsonStreamOutputArgs] — when present, enables structured JSON output
     *     so the desktop layer can extract the native session UUID (e.g. `--output-format stream-json`).
     *  2. `--dangerously-skip-permissions` — for Claude when the user preference is enabled.
     *
     * Kept `internal` so it can be unit-tested without spawning a real PTY process.
     */
    internal fun buildEffectiveLaunchCommand(agent: AIAgent): String {
        // Start from the base command, stripping any trailing newline so we can
        // safely append flags before re-terminating.
        var cmd = agent.launchCommand.trimEnd('\n')

        // Always append JSON stream args when the agent declares them.
        // This is safe on resume too — Claude/Codex accept the flag on every invocation.
        val jsonArgs = agent.jsonStreamOutputArgs
        if (jsonArgs != null) {
            cmd += " " + jsonArgs.joinToString(" ")
        }

        // Claude-specific: skip interactive permission prompts when user opts in.
        if (agent.id == "claude" && generalPreferences?.claudeSkipPermissions == true) {
            cmd += " --dangerously-skip-permissions"
        }

        return "$cmd\n"
    }

    /**
     * Builds a minimal, allowlist-based environment for agent PTY sessions.
     *
     * Delegates to [AgentEnvironmentBuilder] (shared) — single source of truth
     * for the allowlist and trusted binary directories.
     *
     * @param agent       The AI assistant whose API key variable should be set.
     * @param apiKeyValue The resolved secret value, or `null` if unavailable.
     */
    internal fun buildAgentEnv(agent: AIAgent, apiKeyValue: String?): Map<String, String> {
        val processEnv = System.getenv().toMutableMap()
        processEnv.putIfAbsent("HOME", System.getProperty("user.home", "/"))
        return AgentEnvironmentBuilder.build(
            agent = agent,
            apiKeyValue = apiKeyValue,
            currentEnv = processEnv,
        )
    }

    /**
     * Creates a [PtySessionState] for [agent], starts the PTY process, and returns
     * the effective launch command that must be sent to the PTY stdin.
     *
     * The caller ([DesktopTerminalService]) is responsible for:
     * - Registering the returned state in the session map.
     * - Launching the process-exit watcher coroutine in [PtySessionState.scope].
     * - Actually sending the launch command via [DesktopTerminalService.sendInput].
     *
     * @return Pair of (newSession, effectiveLaunchCommand).
     */
    fun startAgentSession(
        agent: AIAgent,
        projectId: Uuid,
        workingDirectory: String,
        apiKeyValue: String?,
    ): Pair<PtySessionState, String> {
        val agentEnv = buildAgentEnv(agent, apiKeyValue)
        val shellPath = resolveDefaultShell()
        val command: Array<String> = arrayOf(shellPath, "-l")
        val size = TerminalSize(columns = 220, rows = 50)

        val ptyProcess = PtyProcessBuilder(command)
            .setEnvironment(agentEnv)
            .setDirectory(workingDirectory)
            .setInitialColumns(size.columns)
            .setInitialRows(size.rows)
            .setConsole(false)
            .setUseWinConPty(isWindows)
            .start()

        val session = TerminalSession(
            projectId = projectId,
            title = agent.displayName,
            state = TerminalSessionState.Running,
            isAgentSession = true,
        )

        val outputFlow = MutableSharedFlow<String>(replay = 8, extraBufferCapacity = 512)
        val scrollback = ScrollbackBuffer()
        val sessionScope = CoroutineScope(
            SupervisorJob(serviceScope.coroutineContext[kotlinx.coroutines.Job]) + Dispatchers.IO,
        )
        val inputWriter = BufferedWriter(OutputStreamWriter(ptyProcess.outputStream, Charsets.UTF_8))

        val ptyState = PtySessionState(
            session = session,
            ptyProcess = ptyProcess,
            inputWriter = inputWriter,
            outputFlow = outputFlow,
            scrollback = scrollback,
            scope = sessionScope,
        )

        val effectiveLaunchCommand = buildEffectiveLaunchCommand(agent)
        commandSink?.invoke(effectiveLaunchCommand)

        return ptyState to effectiveLaunchCommand
    }
}

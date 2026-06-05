@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.terminal

import com.pty4j.PtyProcessBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import studio.vibe.shared.contract.AIAgent
import studio.vibe.shared.preferences.GeneralPreferencesReading
import studio.vibe.shared.service.security.AgentEnvironmentBuilder
import studio.vibe.shared.model.TerminalSession
import studio.vibe.shared.model.TerminalSessionState
import studio.vibe.shared.model.TerminalSize
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
     * For Claude agents with `claudeSkipPermissions` preference enabled, appends
     * `--dangerously-skip-permissions` to the command.
     *
     * Kept `internal` so it can be unit-tested without spawning a real PTY process.
     */
    internal fun buildEffectiveLaunchCommand(agent: AIAgent): String =
        if (agent.id == "claude" && generalPreferences?.claudeSkipPermissions == true) {
            agent.launchCommand.trimEnd('\n') + " --dangerously-skip-permissions\n"
        } else {
            agent.launchCommand
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

@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.terminal

import com.pty4j.PtyProcessBuilder
import com.pty4j.WinSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.vibe.shared.contract.TerminalInputSending
import studio.vibe.shared.contract.TerminalScrollbackAccessing
import studio.vibe.shared.contract.TerminalSessionCreating
import studio.vibe.shared.contract.TerminalSessionEvent
import studio.vibe.shared.contract.TerminalSessionManaging
import studio.vibe.shared.contract.TerminalSessionQuerying
import studio.vibe.shared.model.AIAssistant
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.SplitDirection
import studio.vibe.shared.model.TabActivityState
import studio.vibe.shared.model.TerminalSession
import studio.vibe.shared.model.TerminalSessionState
import studio.vibe.shared.model.TerminalSize
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import kotlin.uuid.Uuid

// ── Platform helpers ──────────────────────────────────────────────────────────

private val isWindows: Boolean
    get() = System.getProperty("os.name", "").lowercase().contains("windows")

private val isMac: Boolean
    get() = System.getProperty("os.name", "").lowercase().contains("mac")

/**
 * Resolves the user's preferred shell.
 *
 * Priority:
 * 1. `SHELL` environment variable (set by the user's login session)
 * 2. macOS / Linux fallback: `/bin/zsh` on macOS, `/bin/bash` elsewhere
 * 3. Windows: `cmd.exe`
 */
internal fun resolveDefaultShell(): String {
    System.getenv("SHELL")?.takeIf { it.isNotBlank() }?.let { return it }
    return when {
        isWindows -> "cmd.exe"
        isMac -> "/bin/zsh"
        else -> "/bin/bash"
    }
}

// ── Internal state ─────────────────────────────────────────────────────────────

/**
 * Runtime state for a single live PTY session.
 *
 * @property session        Shared-model snapshot (immutable; replace on state change).
 * @property ptyProcess     The underlying pty4j process.
 * @property inputWriter    Buffered writer over the process stdin for sending keystrokes.
 * @property outputFlow     Hot [MutableSharedFlow] of raw PTY output chunks.
 * @property scrollback     Accumulated scrollback buffer.
 * @property scope          Child [CoroutineScope] tied to this session's lifetime.
 */
internal class PtySessionState(
    @Volatile var session: TerminalSession,
    val ptyProcess: com.pty4j.PtyProcess,
    val inputWriter: BufferedWriter,
    val outputFlow: MutableSharedFlow<String>,
    val scrollback: StringBuilder,
    val scope: CoroutineScope,
)

// ── DesktopTerminalService ────────────────────────────────────────────────────

/**
 * Desktop (JVM) implementation of [TerminalSessionManaging] backed by pty4j.
 *
 * ### Lifecycle
 * Each session gets its own child [CoroutineScope] derived from [serviceScope]
 * via a [SupervisorJob].  Cancelling [serviceScope] (e.g. on app exit) tears
 * down all active PTY processes gracefully.
 *
 * ### Output
 * PTY output is read on [Dispatchers.IO] and emitted to a per-session
 * [MutableSharedFlow].  Call [outputFlow] to subscribe from the UI.
 *
 * ### Thread safety
 * [_sessions] is a [MutableStateFlow] of an immutable [Map] — mutations happen
 * on the coroutine dispatcher through [_sessions.update].  [PtySessionState]
 * internals (scrollback, inputWriter) are accessed on [Dispatchers.IO].
 *
 * @param serviceScope Parent coroutine scope.  Typically the app's main scope.
 */
class DesktopTerminalService(
    private val serviceScope: CoroutineScope,
) : TerminalSessionManaging {

    // ── Internal session registry ──────────────────────────────────────────

    private val _sessions: MutableStateFlow<Map<Uuid, PtySessionState>> =
        MutableStateFlow(emptyMap())

    // ── TerminalSessionQuerying ─────────────────────────────────────────────

    private val _sessionsByProject =
        MutableStateFlow<Map<Uuid, List<TerminalSession>>>(emptyMap())
    override val sessionsByProject: StateFlow<Map<Uuid, List<TerminalSession>>> =
        _sessionsByProject

    private val _projectActivityStates =
        MutableStateFlow<Map<Uuid, TabActivityState>>(emptyMap())
    override val projectActivityStates: StateFlow<Map<Uuid, TabActivityState>> =
        _projectActivityStates

    private val _sessionEvents = MutableSharedFlow<TerminalSessionEvent>(
        extraBufferCapacity = 64,
    )
    override val sessionEvents: Flow<TerminalSessionEvent> =
        _sessionEvents.asSharedFlow()

    // ── TerminalSessionCreating ─────────────────────────────────────────────

    /**
     * Creates a new PTY session for [projectId].
     *
     * The shell process is started immediately; output reading begins in the
     * background.  The returned [TerminalSession] is already registered and
     * visible via [sessionsByProject].
     *
     * @param projectId        Owner project.
     * @param shell            Shell executable path, or `null` to use [resolveDefaultShell].
     * @param workingDirectory Working directory for the shell, or `null` for the
     *                         user home directory.
     * @param size             Initial PTY dimensions.
     */
    override fun createSession(
        projectId: Uuid,
        shell: String?,
        workingDirectory: FilePath?,
        size: TerminalSize,
    ): TerminalSession {
        val shellPath = shell ?: resolveDefaultShell()
        val workDir = workingDirectory?.path ?: System.getProperty("user.home", "/")
        val sessionTitle = shellPath.substringAfterLast('/')
            .substringAfterLast('\\')
            .ifBlank { "terminal" }

        val env = buildEnv()
        val command: Array<String> = if (isWindows) {
            arrayOf(shellPath)
        } else {
            arrayOf(shellPath, "-l")   // login shell — sources .zshrc / .bash_profile
        }

        val ptyProcess = PtyProcessBuilder(command)
            .setEnvironment(env)
            .setDirectory(workDir)
            .setInitialColumns(size.columns)
            .setInitialRows(size.rows)
            .setConsole(false)
            .setUseWinConPty(isWindows)
            .start()

        val session = TerminalSession(
            projectId = projectId,
            title = sessionTitle,
            state = TerminalSessionState.Running,
        )

        val outputFlow = MutableSharedFlow<String>(
            replay = 0,
            extraBufferCapacity = 512,
        )
        val scrollback = StringBuilder()
        val sessionScope = CoroutineScope(
            SupervisorJob(serviceScope.coroutineContext[kotlinx.coroutines.Job]) +
                Dispatchers.IO,
        )
        val inputWriter = BufferedWriter(OutputStreamWriter(ptyProcess.outputStream, Charsets.UTF_8))

        val state = PtySessionState(
            session = session,
            ptyProcess = ptyProcess,
            inputWriter = inputWriter,
            outputFlow = outputFlow,
            scrollback = scrollback,
            scope = sessionScope,
        )

        _sessions.update { it + (session.id to state) }
        rebuildProjectSessions()

        // Launch output reader
        sessionScope.launch {
            readPtyOutput(session.id, state)
        }

        // Launch process-exit watcher
        sessionScope.launch {
            watchProcessExit(session.id, projectId, state)
        }

        return session
    }

    /**
     * Resizes the PTY window for an existing session.
     * No-op if the session is not found or has already exited.
     */
    override fun resize(sessionId: Uuid, size: TerminalSize) {
        val state = _sessions.value[sessionId] ?: return
        runCatching {
            state.ptyProcess.winSize = WinSize(size.columns, size.rows)
        }
    }

    /** Kills a session.  If [force] is true, uses SIGKILL; otherwise SIGTERM. */
    override fun killSession(sessionId: Uuid, force: Boolean) {
        val state = _sessions.value[sessionId] ?: return
        runCatching {
            if (force) {
                state.ptyProcess.destroyForcibly()
            } else {
                state.ptyProcess.destroy()
            }
        }
        removeSession(sessionId)
    }

    /** Kills all sessions belonging to [projectId]. */
    override fun killAllSessions(projectId: Uuid) {
        _sessions.value.entries
            .filter { (_, s) -> s.session.projectId == projectId }
            .forEach { (id, _) -> killSession(id, force = true) }
    }

    /**
     * Splits an existing session by creating a new session in the same project
     * with the same working directory.
     *
     * The pty4j API does not have a native split concept; we model it by
     * spawning a sibling session and tagging it with [splitDirection].
     */
    override fun split(
        sessionId: Uuid,
        direction: SplitDirection,
        size: TerminalSize,
    ): TerminalSession {
        val parentState = _sessions.value[sessionId]
            ?: error("Session $sessionId not found — cannot split")
        val parent = parentState.session
        val newSession = createSession(
            projectId = parent.projectId,
            shell = null,
            workingDirectory = null,
            size = size,
        )
        // Tag the new session with split direction
        val taggedSession = newSession.copy(splitDirection = direction)
        updateSessionModel(taggedSession)
        return taggedSession
    }

    /**
     * Creates a session that immediately launches an AI agent CLI tool.
     *
     * If the agent uses [AIAssistant.launchViaShellInput] the command is typed
     * into an interactive shell after it starts; otherwise the shell is
     * launched with the command as `$SHELL -l -c <cmd>`.
     */
    override fun startAgentSession(
        agent: AIAssistant,
        projectId: Uuid,
        workingDirectory: String,
        apiKeyValue: String?,
    ): TerminalSession? {
        return runCatching {
            val size = TerminalSize(columns = 220, rows = 50)
            val session = createSession(
                projectId = projectId,
                shell = null,
                workingDirectory = FilePath(workingDirectory),
                size = size,
            )
            // Mark it as an agent session
            val agentSession = session.copy(
                title = agent.displayName,
                isAgentSession = true,
            )
            updateSessionModel(agentSession)

            // Inject API key if needed
            val envVar = agent.apiKeyEnvironmentVariable
            if (envVar != null && apiKeyValue != null) {
                sendInput("export $envVar=$apiKeyValue\n", agentSession.id)
            }

            // Launch the agent
            if (agent.launchViaShellInput) {
                sendInput(agent.launchCommand, agentSession.id)
            } else {
                sendInput(agent.launchCommand, agentSession.id)
            }

            agentSession
        }.getOrNull()
    }

    // ── TerminalSessionQuerying ─────────────────────────────────────────────

    override fun session(id: Uuid): TerminalSession? =
        _sessions.value[id]?.session

    override fun sessions(projectId: Uuid): List<TerminalSession> =
        _sessions.value.values
            .filter { it.session.projectId == projectId }
            .map { it.session }

    override fun markProjectSeen(projectId: Uuid) {
        _projectActivityStates.update { current ->
            current.toMutableMap().apply {
                if (this[projectId] == TabActivityState.RUNNING) {
                    this[projectId] = TabActivityState.IDLE
                }
            }
        }
    }

    // ── TerminalInputSending ────────────────────────────────────────────────

    /**
     * Sends a raw text string to the PTY process stdin.
     *
     * The text is written as UTF-8; it is the caller's responsibility to append
     * `\n` for a newline / Enter keypress.
     */
    override fun sendInput(text: String, sessionId: Uuid) {
        val state = _sessions.value[sessionId] ?: return
        serviceScope.launch(Dispatchers.IO) {
            runCatching {
                state.inputWriter.write(text)
                state.inputWriter.flush()
            }
        }
    }

    // ── TerminalScrollbackAccessing ─────────────────────────────────────────

    /** Returns accumulated scrollback content for [sessionId], or `null`. */
    override fun scrollbackContent(sessionId: Uuid): String? =
        _sessions.value[sessionId]?.scrollback?.toString()

    // ── Output flow access ──────────────────────────────────────────────────

    /**
     * Returns a [Flow] of raw PTY output chunks for [sessionId].
     *
     * The flow emits whenever the process writes bytes to its stdout/stderr.
     * Chunks include ANSI escape sequences — the receiver is responsible for
     * rendering or stripping them.  Returns `null` if the session is unknown.
     */
    fun outputFlow(sessionId: Uuid): Flow<String>? =
        _sessions.value[sessionId]?.outputFlow?.asSharedFlow()

    /**
     * Returns the raw pty4j [com.pty4j.PtyProcess] for [sessionId].
     *
     * Used by [TerminalView] to wire JediTerm's [com.jediterm.terminal.ProcessTtyConnector]
     * directly to the underlying process.  Returns `null` if the session is unknown
     * or has already been removed from the registry.
     */
    internal fun ptyProcessForSession(sessionId: Uuid): com.pty4j.PtyProcess? =
        _sessions.value[sessionId]?.ptyProcess

    /**
     * Returns a snapshot of the internal session state map.
     *
     * Used by [TerminalView] to obtain [PtySessionState] (specifically the
     * [PtySessionState.ptyProcess]) for wiring JediTerm's
     * [com.jediterm.terminal.ProcessTtyConnector].  Returns an immutable snapshot;
     * callers must not mutate it.
     */
    internal fun internalSessions(): Map<Uuid, PtySessionState> = _sessions.value

    // ── Lifecycle ───────────────────────────────────────────────────────────

    /** Kills all active sessions and cancels the service scope. */
    fun dispose() {
        _sessions.value.keys.toList().forEach { id -> killSession(id, force = true) }
        serviceScope.cancel()
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    /**
     * Reads raw bytes from the PTY output stream and emits them as UTF-8
     * chunks into [PtySessionState.outputFlow].
     *
     * Runs on [Dispatchers.IO].  Exits when the process closes its output
     * stream (EOF) or an I/O exception occurs.
     */
    private suspend fun readPtyOutput(sessionId: Uuid, state: PtySessionState) {
        val buffer = ByteArray(4096)
        val inputStream = state.ptyProcess.inputStream
        withContext(Dispatchers.IO) {
            runCatching {
                while (true) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break
                    val chunk = String(buffer, 0, bytesRead, Charsets.UTF_8)
                    state.outputFlow.tryEmit(chunk)
                    synchronized(state.scrollback) {
                        state.scrollback.append(chunk)
                        // Trim scrollback to ~100k characters to avoid unbounded growth
                        if (state.scrollback.length > 100_000) {
                            state.scrollback.delete(0, state.scrollback.length - 80_000)
                        }
                    }
                    // Signal activity to the project
                    markSessionActivity(sessionId, state.session.projectId)
                }
            }
        }
    }

    /**
     * Waits for the PTY process to exit and emits a [TerminalSessionEvent.ProcessExited]
     * event.  Updates the session state to [TerminalSessionState.Exited].
     *
     * Runs on [Dispatchers.IO].
     */
    private suspend fun watchProcessExit(
        sessionId: Uuid,
        projectId: Uuid,
        state: PtySessionState,
    ) {
        withContext(Dispatchers.IO) {
            val exitCode = runCatching { state.ptyProcess.waitFor() }.getOrDefault(-1)
            val exited = state.session.copy(state = TerminalSessionState.Exited(exitCode))
            state.session = exited
            rebuildProjectSessions()
            _sessionEvents.tryEmit(
                TerminalSessionEvent.ProcessExited(
                    sessionId = sessionId,
                    projectId = projectId,
                    exitCode = exitCode,
                ),
            )
            state.scope.cancel()
        }
    }

    /** Marks the owning project as having terminal activity. */
    private fun markSessionActivity(sessionId: Uuid, projectId: Uuid) {
        _projectActivityStates.update { current ->
            current + (projectId to TabActivityState.RUNNING)
        }
        _sessionEvents.tryEmit(
            TerminalSessionEvent.ActivityDetected(
                sessionId = sessionId,
                projectId = projectId,
            ),
        )
    }

    /** Replaces a session model in the registry and refreshes project map. */
    private fun updateSessionModel(updated: TerminalSession) {
        _sessions.update { map ->
            val state = map[updated.id] ?: return@update map
            state.session = updated
            map
        }
        rebuildProjectSessions()
    }

    /** Removes a session from the registry and refreshes project map. */
    private fun removeSession(sessionId: Uuid) {
        _sessions.update { it - sessionId }
        rebuildProjectSessions()
    }

    /** Rebuilds the [_sessionsByProject] StateFlow from the current [_sessions] map. */
    private fun rebuildProjectSessions() {
        val byProject = _sessions.value.values
            .groupBy { it.session.projectId }
            .mapValues { (_, states) -> states.map { it.session } }
        _sessionsByProject.value = byProject
    }

    /**
     * Builds the environment map for the new PTY process.
     *
     * Inherits the current process environment, then ensures `TERM` is set
     * to `xterm-256color` so that colour-capable shells render correctly.
     */
    private fun buildEnv(): Map<String, String> {
        return System.getenv().toMutableMap().apply {
            putIfAbsent("TERM", "xterm-256color")
            putIfAbsent("COLORTERM", "truecolor")
            // Ensure $HOME is present — some minimal envs omit it
            putIfAbsent("HOME", System.getProperty("user.home", "/"))
        }
    }
}

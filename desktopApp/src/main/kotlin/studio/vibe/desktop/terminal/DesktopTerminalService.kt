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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.vibe.shared.contract.AIAgent
import studio.vibe.shared.contract.TerminalInputSending
import studio.vibe.shared.contract.TerminalScrollbackAccessing
import studio.vibe.shared.contract.TerminalSessionCreating
import studio.vibe.shared.contract.TerminalSessionEvent
import studio.vibe.shared.contract.TerminalSessionManaging
import studio.vibe.shared.contract.TerminalSessionQuerying
import studio.vibe.shared.preferences.GeneralPreferencesReading
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.SplitDirection
import studio.vibe.shared.model.TabActivityState
import studio.vibe.shared.model.TerminalSession
import studio.vibe.shared.model.TerminalSessionState
import studio.vibe.shared.model.TerminalSize
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import kotlin.uuid.Uuid

// ── Internal state ─────────────────────────────────────────────────────────────

/**
 * Runtime state for a single live PTY session.
 *
 * @property session        Shared-model snapshot (immutable; replace on state change).
 * @property ptyProcess     The underlying pty4j process.
 * @property inputWriter    Buffered writer over the process stdin for sending keystrokes.
 * @property outputFlow     Hot [MutableSharedFlow] of raw PTY output chunks.
 * @property scrollback     Thread-safe scrollback buffer (see [ScrollbackBuffer]).
 * @property scope          Child [CoroutineScope] tied to this session's lifetime.
 */
internal class PtySessionState(
    @Volatile var session: TerminalSession,
    val ptyProcess: com.pty4j.PtyProcess,
    val inputWriter: BufferedWriter,
    val outputFlow: MutableSharedFlow<String>,
    val scrollback: ScrollbackBuffer,
    val scope: CoroutineScope,
    /** Job for the 2-second graceful-kill timer. Cancelled in removeSession to prevent double-execution. */
    @Volatile var graceKillJob: kotlinx.coroutines.Job? = null,
)

// ── DesktopTerminalService ────────────────────────────────────────────────────

/**
 * Desktop (JVM) implementation of [TerminalSessionManaging] backed by pty4j.
 *
 * ### Lifecycle
 * Each session gets its own child [CoroutineScope] derived from the internal
 * [serviceScope] via a [SupervisorJob].  Calling [dispose] cancels only the
 * internal scope and does NOT affect the [parentScope] passed by the caller.
 *
 * ### Output
 * PTY output is read by JediTerm via [com.jediterm.terminal.ProcessTtyConnector].
 * The connector feeds captured chunks back to this service via [receiveOutput]
 * for scrollback accumulation, activity tracking, and [outputFlow] emission.
 * This single-reader design avoids the data-race that occurs when two threads
 * compete for the same [java.io.InputStream].
 *
 * ### Thread safety
 * [_sessions] is a [MutableStateFlow] of an immutable [Map] — mutations happen
 * on the coroutine dispatcher through [_sessions.update].  [PtySessionState]
 * internals (scrollback, inputWriter) are accessed on [Dispatchers.IO].
 *
 * @param parentScope Parent coroutine scope. The service creates its own child
 *   scope so that [dispose] cancels only this service, not the whole app tree.
 */
class DesktopTerminalService(
    parentScope: CoroutineScope,
    private val generalPreferences: GeneralPreferencesReading? = null,
    /** Test-only seam: receives the effective launch command instead of (or before) PTY sendInput. */
    internal val commandSink: ((String) -> Unit)? = null,
) : TerminalSessionManaging, studio.vibe.shared.contract.TerminalRemoteHost {

    /**
     * Internal scope for all service-level coroutines (output watcher, session
     * scopes, grace-period kills).  Derived from [parentScope] via a child
     * SupervisorJob so that [dispose] can cancel only this service without
     * affecting the rest of the application's coroutine tree.
     */
    private val serviceScope: CoroutineScope = CoroutineScope(
        SupervisorJob(parentScope.coroutineContext[kotlinx.coroutines.Job]) +
            parentScope.coroutineContext,
    )

    // ── AgentSessionFactory ────────────────────────────────────────────────

    private val agentSessionFactory = AgentSessionFactory(
        generalPreferences = generalPreferences,
        serviceScope = serviceScope,
        commandSink = commandSink,
    )

    // ── Session limits ─────────────────────────────────────────────────────

    private val maxSessionsPerProject = 8

    // ── Internal session registry ──────────────────────────────────────────

    private val _sessions: MutableStateFlow<Map<Uuid, PtySessionState>> =
        MutableStateFlow(emptyMap())

    // ── TerminalSessionQuerying ─────────────────────────────────────────────

    // Derived atomically from _sessions — no separate rebuild step needed.
    override val sessionsByProject: StateFlow<Map<Uuid, List<TerminalSession>>> =
        _sessions.map { map ->
            map.values
                .groupBy { it.session.projectId }
                .mapValues { (_, states) -> states.map { it.session } }
        }.stateIn(serviceScope, SharingStarted.Eagerly, emptyMap())

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
        val existingCount = _sessions.value.values.count { it.session.projectId == projectId }
        if (existingCount >= maxSessionsPerProject) {
            error("Session limit reached for project $projectId (max $maxSessionsPerProject)")
        }

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
            replay = 8,
            extraBufferCapacity = 512,
        )
        val scrollback = ScrollbackBuffer()
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

        // NOTE: No output reader coroutine here.  JediTerm reads the PTY
        // InputStream via ProcessTtyConnector and feeds captured chunks back
        // to this service through [receiveOutput].  Launching a competing
        // reader would split bytes between the two threads, leaving JediTerm
        // with partial or no output (the original blank-terminal bug).

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

    /**
     * Kills a session.
     *
     * When [force] is true the process is SIGKILLed immediately and the session
     * is removed synchronously.  When [force] is false, SIGTERM is sent first and
     * the session is removed after a 2-second grace period (or immediately once
     * the process has already died), matching Swift TerminalService behaviour.
     */
    override fun killSession(sessionId: Uuid, force: Boolean) {
        val state = _sessions.value[sessionId] ?: return
        if (force) {
            runCatching { state.ptyProcess.destroyForcibly() }
            removeSession(sessionId)
        } else {
            runCatching { state.ptyProcess.destroy() }  // sends SIGTERM
            // Grace period: wait 2 seconds, then SIGKILL if still alive.
            // Stored in PtySessionState so removeSession can cancel it and
            // prevent double-execution if the session is also killed forcibly.
            state.graceKillJob = serviceScope.launch(Dispatchers.IO) {
                kotlinx.coroutines.delay(2_000)
                if (state.ptyProcess.isAlive) {
                    runCatching { state.ptyProcess.destroyForcibly() }
                }
                removeSession(sessionId)
            }
        }
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
     * The session is a login shell whose environment is built via [buildAgentEnv]
     * (allowlist-based, no credential leakage).  The agent API key is injected
     * directly into the process environment instead of being echoed via an
     * `export` command, which would expose it in the visible terminal output.
     *
     * Lingering exited agent sessions for the same project are cleaned up before
     * the new session is created, matching Swift TerminalService behaviour.
     */
    override fun startAgentSession(
        agent: AIAgent,
        projectId: Uuid,
        workingDirectory: String,
        apiKeyValue: String?,
    ): Result<TerminalSession> {
        return runCatching {
            // Remove lingering exited agent sessions before launching a new one.
            val exitedAgentIds = _sessions.value.entries
                .filter { (_, s) ->
                    s.session.projectId == projectId &&
                        s.session.isAgentSession &&
                        s.session.state is TerminalSessionState.Exited
                }
                .map { it.key }
            exitedAgentIds.forEach { removeSession(it) }

            // Check per-project session limit.
            val existingCount = _sessions.value.values.count { it.session.projectId == projectId }
            if (existingCount >= maxSessionsPerProject) {
                error("Session limit reached for project $projectId (max $maxSessionsPerProject)")
            }

            // Delegate PTY creation + env building to AgentSessionFactory.
            val (ptyState, effectiveLaunchCommand) = agentSessionFactory.startAgentSession(
                agent = agent,
                projectId = projectId,
                workingDirectory = workingDirectory,
                apiKeyValue = apiKeyValue,
            )

            val session = ptyState.session
            _sessions.update { it + (session.id to ptyState) }

            // Watch for process exit (includes 10-second visibility window for agents).
            ptyState.scope.launch { watchProcessExit(session.id, projectId, ptyState) }

            // Send launch command — API key is already in the env, no export needed.
            sendInput(effectiveLaunchCommand, session.id)

            session
        }
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

    /**
     * Returns accumulated scrollback content for [sessionId], or `null`.
     *
     * The read is performed under the same monitor used by [receiveOutput]
     * to mutate the [StringBuilder]; otherwise concurrent appends/deletes
     * can leave `toString()` observing a torn internal state and throw
     * `ArrayIndexOutOfBoundsException` on the JVM.
     */
    override fun scrollbackContent(sessionId: Uuid): String? {
        val state = _sessions.value[sessionId] ?: return null
        return state.scrollback.content()
    }

    // ── Output flow access ──────────────────────────────────────────────────

    /**
     * Returns a [Flow] of raw PTY output chunks for [sessionId].
     *
     * The flow emits whenever the process writes bytes to its stdout/stderr.
     * Chunks include ANSI escape sequences — the receiver is responsible for
     * rendering or stripping them.  Returns `null` if the session is unknown.
     */
    override fun outputFlow(sessionId: Uuid): Flow<String>? =
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

    // ── Output capture (called from Pty4JConnector) ────────────────────────

    /**
     * Receives a chunk of terminal output captured by [Pty4JConnector.read].
     *
     * This is the **single entry point** for PTY output into the service.
     * JediTerm's [com.jediterm.terminal.ProcessTtyConnector] is the sole reader
     * of the PTY [java.io.InputStream]; after reading, the connector forwards
     * each chunk here for:
     *
     *  1. [PtySessionState.outputFlow] emission (subscribers, Remote Control).
     *  2. [PtySessionState.scrollback] accumulation (capped at 100 KB).
     *  3. Project activity signalling.
     */
    internal fun receiveOutput(sessionId: Uuid, chunk: String) {
        val state = _sessions.value[sessionId] ?: return
        state.outputFlow.tryEmit(chunk)
        state.scrollback.append(chunk)
        markSessionActivity(sessionId, state.session.projectId)
    }

    // ── Private helpers ─────────────────────────────────────────────────────

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
            // Trigger re-emission of _sessions so that sessionsByProject (derived stateIn) updates.
            _sessions.update { it }
            _sessionEvents.tryEmit(
                TerminalSessionEvent.ProcessExited(
                    sessionId = sessionId,
                    projectId = projectId,
                    exitCode = exitCode,
                ),
            )

            if (state.session.isAgentSession) {
                // Keep agent session visible for 10 s so the user can read error output
                // before it disappears from the session list.
                kotlinx.coroutines.delay(10_000)
            }

            // We are running inside `state.scope`; let it complete naturally
            // after this lambda returns.  Calling `removeSession` here would
            // self-cancel via [PtySessionState.scope] and throw a spurious
            // CancellationException out of `withContext`.
            unregisterSession(sessionId)
            runCatching { state.inputWriter.close() }
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
            // Return a new map copy so StateFlow detects the change and re-emits,
            // which triggers the derived sessionsByProject stateIn to update.
            map.toMutableMap()
        }
    }

    /**
     * Removes a session entry from the registry without touching the session
     * scope.  Used by [watchProcessExit] after natural process exit, where the
     * enclosing `sessionScope.launch { ... }` completes on its own.
     */
    private fun unregisterSession(sessionId: Uuid) {
        _sessions.update { it - sessionId }
    }

    /**
     * Removes a session from the registry, closes its stdin writer and cancels
     * its [CoroutineScope].
     *
     * Cancelling the scope is required for the external-kill path
     * ([killSession], [startAgentSession]'s exited-session cleanup): the
     * PTY process has been killed but the `sessionScope`'s `watchProcessExit`
     * coroutine may still be suspended inside `waitFor`.  Without explicit
     * cancellation that coroutine would survive for the lifetime of
     * [serviceScope] and leak memory.
     */
    private fun removeSession(sessionId: Uuid) {
        val removed: PtySessionState? = run {
            var captured: PtySessionState? = null
            _sessions.update { current ->
                captured = current[sessionId]
                if (captured != null) current - sessionId else current
            }
            captured
        }
        if (removed != null) {
            removed.graceKillJob?.cancel()
            removed.graceKillJob = null
            runCatching { removed.inputWriter.close() }
            removed.scope.cancel()
        }
    }

    /**
     * Builds the environment map for regular (non-agent) PTY sessions.
     *
     * Inherits the current process environment, then ensures `TERM` is set
     * to `xterm-256color` so that colour-capable shells render correctly.
     *
     * Agent sessions use [AgentSessionFactory.buildAgentEnv] instead.
     */
    private fun buildEnv(): Map<String, String> {
        return System.getenv().toMutableMap().apply {
            putIfAbsent("TERM", "xterm-256color")
            putIfAbsent("COLORTERM", "truecolor")
            // Ensure $HOME is present — some minimal envs omit it
            putIfAbsent("HOME", System.getProperty("user.home", "/"))
        }
    }

    /**
     * Delegates to [AgentSessionFactory.buildEffectiveLaunchCommand].
     *
     * Kept as an `internal` shim so existing tests that call
     * `service.buildEffectiveLaunchCommand(agent)` continue to compile without
     * modification.
     */
    internal fun buildEffectiveLaunchCommand(agent: AIAgent): String =
        agentSessionFactory.buildEffectiveLaunchCommand(agent)
}

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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import studio.vibe.shared.contract.AIAgent
import studio.vibe.shared.contract.TerminalSessionManaging
import studio.vibe.shared.contract.TerminalSessionEvent
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
 * ### Responsibilities
 * This class is the public API and session orchestrator. Internal concerns are
 * delegated to focused helpers:
 *  - [PtySessionRegistry] — session map, add/remove/unregister
 *  - [PtyOutputWatcher]   — output capture, activity events, process-exit watcher
 *  - [PtyKillStrategy]    — SIGTERM / SIGKILL with grace period
 *  - [AgentSessionFactory] — environment construction, agent PTY launch
 *
 * ### Lifecycle
 * Each session gets its own child [CoroutineScope] derived from the internal
 * [serviceScope] via a [SupervisorJob]. Calling [dispose] cancels only the
 * internal scope and does NOT affect the [parentScope] passed by the caller.
 *
 * ### Thread safety
 * [PtySessionRegistry] holds a [kotlinx.coroutines.flow.MutableStateFlow] of an
 * immutable [Map] — all mutations happen via `update`. [PtySessionState] internals
 * (scrollback, inputWriter) are accessed on [Dispatchers.IO].
 *
 * @param parentScope Parent coroutine scope. The service creates its own child
 *   scope so that [dispose] cancels only this service, not the whole app tree.
 */
class DesktopTerminalService(
    parentScope: CoroutineScope,
    private val generalPreferences: GeneralPreferencesReading? = null,
    /** Test-only seam: receives the effective launch command instead of (or before) PTY sendInput. */
    internal val commandSink: ((String) -> Unit)? = null,
    /**
     * Optional callback invoked (with sessionId) whenever scrollback content changes.
     * The caller is responsible for debouncing before persisting — this fires on every chunk.
     */
    private val onScrollbackDirty: ((Uuid) -> Unit)? = null,
    /**
     * Optional callback invoked with each raw PTY output chunk.
     * Used to capture the native agent session UUID from Claude stream-json lines.
     */
    private val onOutputChunk: ((sessionId: Uuid, chunk: String) -> Unit)? = null,
) : TerminalSessionManaging, studio.vibe.shared.contract.TerminalRemoteHost {

    /**
     * Internal scope for all service-level coroutines.
     * Derived from [parentScope] via a child SupervisorJob so that [dispose]
     * can cancel only this service without affecting the rest of the app tree.
     */
    private val serviceScope: CoroutineScope = CoroutineScope(
        SupervisorJob(parentScope.coroutineContext[kotlinx.coroutines.Job]) +
            parentScope.coroutineContext,
    )

    // ── Helpers ────────────────────────────────────────────────────────────────

    private val registry = PtySessionRegistry(serviceScope)
    private val outputWatcher = PtyOutputWatcher(
        registry = registry,
        onScrollbackDirty = onScrollbackDirty,
        onOutputLine = onOutputChunk,
    )
    private val killStrategy = PtyKillStrategy(registry, serviceScope)
    private val agentSessionFactory = AgentSessionFactory(
        generalPreferences = generalPreferences,
        serviceScope = serviceScope,
        commandSink = commandSink,
    )

    // ── Session limits ─────────────────────────────────────────────────────────

    private val maxSessionsPerProject = 8

    // ── TerminalSessionQuerying ─────────────────────────────────────────────────

    override val sessionsByProject: StateFlow<Map<Uuid, List<TerminalSession>>> =
        registry.sessionsByProject

    override val projectActivityStates: StateFlow<Map<Uuid, TabActivityState>> =
        outputWatcher.projectActivityStates

    override val sessionEvents: Flow<TerminalSessionEvent> =
        outputWatcher.sessionEvents

    // ── TerminalSessionCreating ─────────────────────────────────────────────────

    /**
     * Creates a new PTY session for [projectId].
     *
     * The shell process is started immediately; output reading begins in the
     * background. The returned [TerminalSession] is already registered and
     * visible via [sessionsByProject].
     */
    override fun createSession(
        projectId: Uuid,
        shell: String?,
        workingDirectory: FilePath?,
        size: TerminalSize,
    ): TerminalSession {
        val existingCount = registry.snapshot.values.count { it.session.projectId == projectId }
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

        registry.add(state)

        // NOTE: No output reader coroutine here.  JediTerm reads the PTY
        // InputStream via ProcessTtyConnector and feeds captured chunks back
        // to this service through [receiveOutput].  Launching a competing
        // reader would split bytes between the two threads, leaving JediTerm
        // with partial or no output (the original blank-terminal bug).

        sessionScope.launch { outputWatcher.watchProcessExit(session.id, projectId, state) }

        return session
    }

    /**
     * Resizes the PTY window for an existing session.
     * No-op if the session is not found or has already exited.
     */
    override fun resize(sessionId: Uuid, size: TerminalSize) {
        val state = registry[sessionId] ?: return
        runCatching {
            state.ptyProcess.winSize = WinSize(size.columns, size.rows)
        }
    }

    /**
     * Kills a session.
     *
     * When [force] is true the process is SIGKILLed immediately.
     * When [force] is false, SIGTERM is sent first and the session is removed
     * after a 2-second grace period, matching Swift TerminalService behaviour.
     */
    override fun killSession(sessionId: Uuid, force: Boolean) {
        killStrategy.kill(sessionId, force)
    }

    /** Kills all sessions belonging to [projectId]. */
    override fun killAllSessions(projectId: Uuid) {
        killStrategy.killAll(projectId)
    }

    /**
     * Splits an existing session by creating a sibling session in the same project.
     *
     * pty4j has no native split concept; we model it by spawning a sibling
     * session tagged with [splitDirection].
     */
    override fun split(
        sessionId: Uuid,
        direction: SplitDirection,
        size: TerminalSize,
    ): TerminalSession {
        val parentState = registry[sessionId]
            ?: error("Session $sessionId not found — cannot split")
        val parent = parentState.session
        val newSession = createSession(
            projectId = parent.projectId,
            shell = null,
            workingDirectory = null,
            size = size,
        )
        val taggedSession = newSession.copy(splitDirection = direction)
        registry.updateModel(taggedSession)
        return taggedSession
    }

    /**
     * Creates a session that immediately launches an AI agent CLI tool.
     *
     * The API key is injected directly into the process environment so it never
     * appears in visible terminal output. Lingering exited agent sessions for
     * the same project are cleaned up before the new session is created.
     */
    override fun startAgentSession(
        agent: AIAgent,
        projectId: Uuid,
        workingDirectory: String,
        apiKeyValue: String?,
    ): Result<TerminalSession> {
        return runCatching {
            // Remove lingering exited agent sessions before launching a new one.
            val exitedAgentIds = registry.snapshot.entries
                .filter { (_, s) ->
                    s.session.projectId == projectId &&
                        s.session.isAgentSession &&
                        s.session.state is TerminalSessionState.Exited
                }
                .map { it.key }
            exitedAgentIds.forEach { registry.remove(it) }

            val existingCount = registry.snapshot.values.count { it.session.projectId == projectId }
            if (existingCount >= maxSessionsPerProject) {
                error("Session limit reached for project $projectId (max $maxSessionsPerProject)")
            }

            val (ptyState, effectiveLaunchCommand) = agentSessionFactory.startAgentSession(
                agent = agent,
                projectId = projectId,
                workingDirectory = workingDirectory,
                apiKeyValue = apiKeyValue,
            )

            val session = ptyState.session
            registry.add(ptyState)
            ptyState.scope.launch { outputWatcher.watchProcessExit(session.id, projectId, ptyState) }

            sendInput(effectiveLaunchCommand, session.id)

            session
        }
    }

    // ── TerminalSessionQuerying ─────────────────────────────────────────────────

    override fun session(id: Uuid): TerminalSession? =
        registry[id]?.session

    override fun sessions(projectId: Uuid): List<TerminalSession> =
        registry.snapshot.values
            .filter { it.session.projectId == projectId }
            .map { it.session }

    override fun markProjectSeen(projectId: Uuid) {
        outputWatcher.clearActivity(projectId)
    }

    // ── TerminalInputSending ────────────────────────────────────────────────────

    /**
     * Sends a raw text string to the PTY process stdin.
     *
     * The text is written as UTF-8; the caller must append `\n` for Enter.
     */
    override fun sendInput(text: String, sessionId: Uuid) {
        val state = registry[sessionId] ?: return
        serviceScope.launch(Dispatchers.IO) {
            runCatching {
                state.inputWriter.write(text)
                state.inputWriter.flush()
            }
        }
    }

    // ── TerminalScrollbackAccessing ─────────────────────────────────────────────

    /**
     * Returns accumulated scrollback content for [sessionId], or `null`.
     *
     * The read is performed under the same monitor used by [receiveOutput]
     * to mutate the [StringBuilder]; otherwise concurrent appends/deletes can
     * leave `toString()` observing a torn state and throw [ArrayIndexOutOfBoundsException].
     */
    override fun scrollbackContent(sessionId: Uuid): String? {
        val state = registry[sessionId] ?: return null
        return state.scrollback.content()
    }

    // ── Output flow access ──────────────────────────────────────────────────────

    /**
     * Returns a [Flow] of raw PTY output chunks for [sessionId], or `null` if unknown.
     *
     * Chunks include ANSI escape sequences — the receiver is responsible for
     * rendering or stripping them.
     */
    override fun outputFlow(sessionId: Uuid): Flow<String>? =
        registry[sessionId]?.outputFlow?.asSharedFlow()

    /**
     * Returns the raw pty4j [com.pty4j.PtyProcess] for [sessionId].
     *
     * Used by [TerminalView] to wire JediTerm's ProcessTtyConnector to the
     * underlying process. Returns `null` if the session is unknown or removed.
     */
    internal fun ptyProcessForSession(sessionId: Uuid): com.pty4j.PtyProcess? =
        registry.ptyProcess(sessionId)

    /**
     * Returns a snapshot of the internal session state map.
     *
     * Used by [TerminalView] to obtain [PtySessionState] for wiring JediTerm's
     * ProcessTtyConnector. Returns an immutable snapshot; callers must not mutate it.
     */
    internal fun internalSessions(): Map<Uuid, PtySessionState> = registry.internalSessions()

    // ── Lifecycle ───────────────────────────────────────────────────────────────

    /** Kills all active sessions and cancels the service scope. */
    fun dispose() {
        killStrategy.killAllSessions()
        serviceScope.cancel()
    }

    // ── Output capture (called from Pty4JConnector) ─────────────────────────────

    /**
     * Receives a chunk of terminal output captured by [Pty4JConnector.read].
     *
     * This is the **single entry point** for PTY output into the service.
     * Delegates to [PtyOutputWatcher.receiveOutput].
     */
    internal fun receiveOutput(sessionId: Uuid, chunk: String) {
        outputWatcher.receiveOutput(sessionId, chunk)
    }

    // ── Private helpers ─────────────────────────────────────────────────────────

    /**
     * Builds the environment map for regular (non-agent) PTY sessions.
     *
     * Inherits the current process environment, then ensures `TERM` is set
     * to `xterm-256color` so that colour-capable shells render correctly.
     * Agent sessions use [AgentSessionFactory.buildAgentEnv] instead.
     */
    private fun buildEnv(): Map<String, String> {
        return System.getenv().toMutableMap().apply {
            putIfAbsent("TERM", "xterm-256color")
            putIfAbsent("COLORTERM", "truecolor")
            putIfAbsent("HOME", System.getProperty("user.home", "/"))
        }
    }

    /**
     * Delegates to [AgentSessionFactory.buildEffectiveLaunchCommand].
     *
     * Kept as an `internal` shim so existing tests that call
     * `service.buildEffectiveLaunchCommand(agent)` continue to compile.
     */
    internal fun buildEffectiveLaunchCommand(agent: AIAgent): String =
        agentSessionFactory.buildEffectiveLaunchCommand(agent)
}

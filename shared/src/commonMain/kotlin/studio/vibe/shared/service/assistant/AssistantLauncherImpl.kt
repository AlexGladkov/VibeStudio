@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.service.assistant

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import studio.vibe.shared.contract.AgentSessionLog
import studio.vibe.shared.contract.AgentSessionRecord
import studio.vibe.shared.contract.AIAgentRegistry
import studio.vibe.shared.contract.APIKeyResolving
import studio.vibe.shared.contract.AgentAvailabilityChecking
import studio.vibe.shared.contract.AgentAvailabilityStatus
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.contract.TerminalSessionManaging
import studio.vibe.shared.model.AgentExitSequence
import studio.vibe.shared.model.TerminalSession
import studio.vibe.shared.usecase.AssistantLauncher
import studio.vibe.shared.usecase.ResumeRequest
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.Uuid

/**
 * Unified state for a single [AssistantLauncherImpl] instance.
 *
 * Merges [sessionIds] (project → agent → sessionId) and the derived
 * [runningByProject] (project → set of agentIds) into one atomic structure so
 * there is never a TOCTOU window between the two.
 */
private data class LauncherState(
    /** project → (agentId → sessionId) */
    val sessionIds: Map<Uuid, Map<String, Uuid>> = emptyMap(),
) {
    /** Derived: project → set of running agentIds (no separate StateFlow needed). */
    val runningByProject: Map<Uuid, Set<String>>
        get() = sessionIds.mapValues { (_, agentMap) -> agentMap.keys }

    fun withSession(projectId: Uuid, agentId: String, sessionId: Uuid): LauncherState {
        val forProject = (sessionIds[projectId] ?: emptyMap()) + (agentId to sessionId)
        return copy(sessionIds = sessionIds + (projectId to forProject))
    }

    fun withoutSession(projectId: Uuid, agentId: String): LauncherState {
        val forProject = (sessionIds[projectId] ?: emptyMap()) - agentId
        return if (forProject.isEmpty()) copy(sessionIds = sessionIds - projectId)
        else copy(sessionIds = sessionIds + (projectId to forProject))
    }

    fun withoutSessionId(projectId: Uuid, sessionId: Uuid): LauncherState {
        val forProject = (sessionIds[projectId] ?: emptyMap()).filterValues { it != sessionId }
        return if (forProject.isEmpty()) copy(sessionIds = sessionIds - projectId)
        else copy(sessionIds = sessionIds + (projectId to forProject))
    }

    fun withoutProject(projectId: Uuid): LauncherState =
        copy(sessionIds = sessionIds - projectId)
}

/**
 * Production implementation of [AssistantLauncher].
 *
 * Encapsulates the start/stop logic that previously lived in [ToolbarViewModel],
 * making it reusable by [RemoteControlServer] without introducing a ViewModel
 * dependency in the network layer.
 *
 * Thread safety: all mutations go through [MutableStateFlow.update] which is atomic.
 * [LauncherState] is a single immutable value — no split-brain between two flows.
 *
 * @param blockingDispatcher Dispatcher for PTY process start (blocking I/O).
 *   Defaults to [Dispatchers.Default]; inject [UnconfinedTestDispatcher] in tests.
 */
@OptIn(ExperimentalTime::class)
class AssistantLauncherImpl(
    private val projectManaging: ProjectManaging,
    private val terminalSessionManaging: TerminalSessionManaging,
    private val agentRegistry: AIAgentRegistry,
    private val agentAvailabilityChecking: AgentAvailabilityChecking,
    private val apiKeyResolving: APIKeyResolving,
    private val blockingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Optional override for env-var resolution (e.g. from process environment on JVM). */
    override var onResolveEnvVar: ((String) -> String?)? = null,
    /**
     * Coroutine scope used to share derived [StateFlow]s via [stateIn].
     * Defaults to a standalone scope with [SupervisorJob] — suitable for production.
     * Inject a [TestScope] (or [backgroundScope] from [runTest]) in tests.
     */
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    /**
     * Optional session log for persisting agent session records.
     * When null, no session metadata is written (backward-compatible default).
     */
    private val agentSessionLog: AgentSessionLog? = null,
) : AssistantLauncher {

    // ── State ──────────────────────────────────────────────────────────────────

    /**
     * Single source of truth. Merges what used to be [_sessionIds] and
     * [_runningByProject] so all mutations are atomic in one [update] call.
     */
    private val _state = MutableStateFlow(LauncherState())

    /**
     * StateFlow view of [LauncherState.runningByProject].
     *
     * Implemented via [stateIn] so the flow is properly collectable —
     * avoiding the fragile anonymous-class pattern with [error]("unreachable").
     */
    override val runningByProject: StateFlow<Map<Uuid, Set<String>>> =
        _state.map { it.runningByProject }
            .stateIn(scope, SharingStarted.Eagerly, _state.value.runningByProject)

    /** Live snapshot of project → agent → sessionId mappings. */
    val agentSessionIds: StateFlow<Map<Uuid, Map<String, Uuid>>> =
        _state.map { it.sessionIds }
            .stateIn(scope, SharingStarted.Eagerly, _state.value.sessionIds)

    // ── AssistantLauncher ─────────────────────────────────────────────────────

    override suspend fun start(
        projectId: Uuid,
        agentId: String,
        resume: ResumeRequest?,
    ): Result<TerminalSession> {
        val project = projectManaging.projects.value.firstOrNull { it.id == projectId }
            ?: return Result.failure(IllegalArgumentException("Project not found: $projectId"))

        val agent = agentRegistry.byId(agentId)
            ?: return Result.failure(IllegalArgumentException("Unknown agent id: $agentId"))

        val availability = agentAvailabilityChecking.check(agent)
        if (availability is AgentAvailabilityStatus.NotInstalled) {
            return Result.failure(IllegalStateException(availability.installHint))
        }

        val apiKeyValue = resolveApiKey(agentId)
        val workingDirectory = project.path.path

        // Build the effective agent: if resume is requested and the agent supports it,
        // wrap it to inject resume arguments into the launch command.
        val effectiveAgent = if (resume != null) {
            val resumeArgs = agent.resumeArgsFor(resume.nativeSessionId)
            if (resumeArgs != null) ResumeWrappedAgent(agent, resumeArgs) else agent
        } else {
            agent
        }

        val result = withContext(blockingDispatcher) {
            terminalSessionManaging.startAgentSession(
                agent = effectiveAgent,
                projectId = projectId,
                workingDirectory = workingDirectory,
                apiKeyValue = apiKeyValue,
            )
        }

        result.onSuccess { session ->
            _state.update { it.withSession(projectId, agentId, session.id) }

            // Persist the session record asynchronously — failures must not block the caller.
            agentSessionLog?.let { log ->
                try {
                    log.append(
                        AgentSessionRecord(
                            sessionId = session.id.toString(),
                            projectId = projectId.toString(),
                            agentId = agentId,
                            startedAt = Clock.System.now().toEpochMilliseconds(),
                        )
                    )
                } catch (e: Exception) {
                    println("AssistantLauncherImpl: failed to log session start: ${e.message}")
                }
            }
        }

        return result
    }

    override suspend fun stop(projectId: Uuid, agentId: String): Result<Unit> {
        val agent = agentRegistry.byId(agentId)
            ?: return Result.failure(IllegalArgumentException("Unknown agent id: $agentId"))

        val sessionId = _state.value.sessionIds[projectId]?.get(agentId)
            ?: return Result.success(Unit) // already stopped — idempotent

        // Send exit signal FIRST — if sendInput throws, the OS process is orphaned
        // but we still clean up state so the UI is consistent (zombie OS process is
        // acceptable at exit; dangling state that blocks re-launch is not).
        //
        // Note: do NOT use runCatching here — it catches CancellationException,
        // which would suppress cooperative cancellation and leak the OS process.
        val sendResult: Result<Unit> = try {
            when (val exitSeq = agent.exitSequence) {
                is AgentExitSequence.CtrlC -> {
                    terminalSessionManaging.sendInput("", sessionId)
                }
                is AgentExitSequence.CtrlCThenCommand -> {
                    terminalSessionManaging.sendInput("", sessionId)
                    kotlinx.coroutines.delay(300)
                    terminalSessionManaging.sendInput("${exitSeq.command}\n", sessionId)
                }
            }
            Result.success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e  // propagate cooperative cancellation — do not swallow
        } catch (e: Exception) {
            Result.failure(e)
        }

        if (sendResult.isFailure) {
            println("AssistantLauncherImpl: sendInput failed (session may already be dead): ${sendResult.exceptionOrNull()?.message}")
        }

        // Remove from state AFTER send attempt — always, regardless of sendInput outcome.
        _state.update { it.withoutSession(projectId, agentId) }

        return sendResult
    }

    // ── Helpers for ToolbarViewModel / session lifecycle ──────────────────────

    /**
     * Returns the session ID for a running agent, or null if not running.
     * Used by [ToolbarViewModel.rebuildState] to populate [ToolbarState.activeAgentSessionId].
     */
    override fun sessionIdFor(projectId: Uuid, agentId: String): Uuid? =
        _state.value.sessionIds[projectId]?.get(agentId)

    /**
     * Called when a PTY process-exit event is received so the launcher stays
     * consistent with actual process state.
     *
     * Single [_state.update] lambda ensures atomicity — no TOCTOU window.
     */
    override fun notifySessionExited(projectId: Uuid, sessionId: Uuid) {
        _state.update { it.withoutSessionId(projectId, sessionId) }
    }

    /** Clean up all state for a removed project. */
    override fun removeProject(projectId: Uuid) {
        _state.update { it.withoutProject(projectId) }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun resolveApiKey(agentId: String): String? {
        val agent = agentRegistry.byId(agentId) ?: return null
        val envVar = agent.apiKeyEnvironmentVariable ?: return null
        return onResolveEnvVar?.invoke(envVar) ?: apiKeyResolving.resolve(envVar)
    }
}

// ── ResumeWrappedAgent ────────────────────────────────────────────────────────

/**
 * Transparent [AIAgent] wrapper that appends [resumeArgs] to [launchArguments].
 *
 * All other properties are forwarded unchanged to [delegate].  This keeps the
 * core agent objects immutable while allowing per-launch customisation.
 */
private class ResumeWrappedAgent(
    private val delegate: studio.vibe.shared.contract.AIAgent,
    private val resumeArgs: List<String>,
) : studio.vibe.shared.contract.AIAgent by delegate {
    override val launchArguments: List<String>
        get() = delegate.launchArguments + resumeArgs
}

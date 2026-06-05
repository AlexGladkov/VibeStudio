@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.service.assistant

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import studio.vibe.shared.contract.AIAgentRegistry
import studio.vibe.shared.contract.APIKeyResolving
import studio.vibe.shared.contract.AgentAvailabilityChecking
import studio.vibe.shared.contract.AgentAvailabilityStatus
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.contract.TerminalSessionManaging
import studio.vibe.shared.model.AgentExitSequence
import studio.vibe.shared.model.TerminalSession
import studio.vibe.shared.usecase.AssistantLauncher
import kotlin.uuid.Uuid

/**
 * Production implementation of [AssistantLauncher].
 *
 * Encapsulates the start/stop logic that previously lived in [ToolbarViewModel],
 * making it reusable by [RemoteControlServer] without introducing a ViewModel
 * dependency in the network layer.
 *
 * Thread safety: all [MutableStateFlow] mutations go through [MutableStateFlow.update]
 * which is atomic. No concurrent modification issues.
 *
 * @param blockingDispatcher Dispatcher for PTY process start (blocking I/O).
 *   Defaults to [Dispatchers.Default]; inject [UnconfinedTestDispatcher] in tests.
 */
class AssistantLauncherImpl(
    private val projectManaging: ProjectManaging,
    private val terminalSessionManaging: TerminalSessionManaging,
    private val agentRegistry: AIAgentRegistry,
    private val agentAvailabilityChecking: AgentAvailabilityChecking,
    private val apiKeyResolving: APIKeyResolving,
    private val blockingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Optional override for env-var resolution (e.g. from process environment on JVM). */
    override var onResolveEnvVar: ((String) -> String?)? = null,
) : AssistantLauncher {

    // ── State ──────────────────────────────────────────────────────────────────

    private val _runningByProject = MutableStateFlow<Map<Uuid, Set<String>>>(emptyMap())
    override val runningByProject: StateFlow<Map<Uuid, Set<String>>> =
        _runningByProject.asStateFlow()

    /**
     * Internal map: projectId → (agentId → sessionId).
     * Exposed via [sessionIdFor] so [ToolbarViewModel] can surface the active
     * session UUID in its toolbar state without duplicating tracking.
     */
    private val _sessionIds = MutableStateFlow<Map<Uuid, Map<String, Uuid>>>(emptyMap())

    /** Live snapshot of project → agent → sessionId mappings. */
    val agentSessionIds: StateFlow<Map<Uuid, Map<String, Uuid>>> = _sessionIds.asStateFlow()

    // ── AssistantLauncher ─────────────────────────────────────────────────────

    override suspend fun start(projectId: Uuid, agentId: String): Result<TerminalSession> {
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

        val result = withContext(blockingDispatcher) {
            terminalSessionManaging.startAgentSession(
                agent = agent,
                projectId = projectId,
                workingDirectory = workingDirectory,
                apiKeyValue = apiKeyValue,
            )
        }

        result.onSuccess { session ->
            _sessionIds.update { current ->
                val forProject = (current[projectId] ?: emptyMap()) + (agentId to session.id)
                current + (projectId to forProject)
            }
            _runningByProject.update { current ->
                val forProject = (current[projectId] ?: emptySet()) + agentId
                current + (projectId to forProject)
            }
        }

        return result
    }

    override suspend fun stop(projectId: Uuid, agentId: String): Result<Unit> {
        val agent = agentRegistry.byId(agentId)
            ?: return Result.failure(IllegalArgumentException("Unknown agent id: $agentId"))

        val sessionId = _sessionIds.value[projectId]?.get(agentId)
            ?: return Result.success(Unit) // already stopped — idempotent

        // Optimistic state removal before sending exit signal
        removeRunning(projectId, agentId)

        return runCatching {
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
        }
    }

    // ── Helpers for ToolbarViewModel / session lifecycle ──────────────────────

    /**
     * Returns the session ID for a running agent, or null if not running.
     * Used by [ToolbarViewModel.rebuildState] to populate [ToolbarState.activeAgentSessionId].
     */
    override fun sessionIdFor(projectId: Uuid, agentId: String): Uuid? =
        _sessionIds.value[projectId]?.get(agentId)

    /**
     * Called when a PTY process-exit event is received so the launcher stays
     * consistent with actual process state.
     *
     * The two StateFlow mutations are made atomic with respect to each other by
     * computing the post-removal agent key set inside the first [update] lambda
     * and reusing that snapshot in the second — eliminating the TOCTOU window
     * that existed when the second lambda read [_sessionIds.value] independently.
     */
    override fun notifySessionExited(projectId: Uuid, sessionId: Uuid) {
        var stillRunningSnapshot: Set<String> = emptySet()
        _sessionIds.update { current ->
            val forProject = current[projectId] ?: return@update current
            val filtered = forProject.filterValues { it != sessionId }
            stillRunningSnapshot = filtered.keys
            if (filtered.isEmpty()) current - projectId
            else current + (projectId to filtered)
        }
        _runningByProject.update { current ->
            if (stillRunningSnapshot.isEmpty()) current - projectId
            else current + (projectId to stillRunningSnapshot)
        }
    }

    /** Clean up all state for a removed project. */
    override fun removeProject(projectId: Uuid) {
        _sessionIds.update { it - projectId }
        _runningByProject.update { it - projectId }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun removeRunning(projectId: Uuid, agentId: String) {
        _sessionIds.update { current ->
            val forProject = (current[projectId] ?: emptyMap()) - agentId
            if (forProject.isEmpty()) current - projectId
            else current + (projectId to forProject)
        }
        _runningByProject.update { current ->
            val forProject = (current[projectId] ?: emptySet()) - agentId
            if (forProject.isEmpty()) current - projectId
            else current + (projectId to forProject)
        }
    }

    private fun resolveApiKey(agentId: String): String? {
        val agent = agentRegistry.byId(agentId) ?: return null
        val envVar = agent.apiKeyEnvironmentVariable ?: return null
        return onResolveEnvVar?.invoke(envVar) ?: apiKeyResolving.resolve(envVar)
    }
}

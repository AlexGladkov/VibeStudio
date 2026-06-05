@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.testutil

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import studio.vibe.shared.model.TerminalSession
import studio.vibe.shared.model.TerminalSessionState
import studio.vibe.shared.usecase.AssistantLauncher
import kotlin.uuid.Uuid

/**
 * In-memory [AssistantLauncher] for ViewModel unit tests.
 *
 * Provides controllable [startResult] / [stopResult] so individual tests can
 * drive success and failure paths without spinning up a real PTY process.
 */
class FakeAssistantLauncher : AssistantLauncher {

    // ── Controllable results ──────────────────────────────────────────────────

    /** Override to make [start] return a failure. Default: success with a fake session. */
    var startResult: ((projectId: Uuid, agentId: String) -> Result<TerminalSession>)? = null

    /** Override to make [stop] return a failure. Default: success. */
    var stopResult: Result<Unit> = Result.success(Unit)

    // ── Call recording ────────────────────────────────────────────────────────

    val startCalls: MutableList<Pair<Uuid, String>> = mutableListOf()
    val stopCalls: MutableList<Pair<Uuid, String>> = mutableListOf()

    val startCallCount: Int get() = startCalls.size
    val stopCallCount: Int get() = stopCalls.size

    // ── State ──────────────────────────────────────────────────────────────────

    private val _runningByProject = MutableStateFlow<Map<Uuid, Set<String>>>(emptyMap())
    override val runningByProject: StateFlow<Map<Uuid, Set<String>>> =
        _runningByProject.asStateFlow()

    /** Active sessions: projectId → agentId → sessionId. */
    private val sessions = mutableMapOf<Uuid, MutableMap<String, Uuid>>()

    // ── AssistantLauncher ─────────────────────────────────────────────────────

    override suspend fun start(projectId: Uuid, agentId: String): Result<TerminalSession> {
        startCalls.add(projectId to agentId)

        val result = startResult?.invoke(projectId, agentId) ?: run {
            val session = TerminalSession(
                projectId = projectId,
                title = agentId,
                state = TerminalSessionState.Running,
                isAgentSession = true,
            )
            Result.success(session)
        }

        result.onSuccess { session ->
            sessions.getOrPut(projectId) { mutableMapOf() }[agentId] = session.id
            _runningByProject.update { current ->
                current + (projectId to ((current[projectId] ?: emptySet()) + agentId))
            }
        }

        return result
    }

    override suspend fun stop(projectId: Uuid, agentId: String): Result<Unit> {
        stopCalls.add(projectId to agentId)
        sessions[projectId]?.remove(agentId)
        _runningByProject.update { current ->
            val remaining = (current[projectId] ?: emptySet()) - agentId
            if (remaining.isEmpty()) current - projectId
            else current + (projectId to remaining)
        }
        return stopResult
    }

    // ── AssistantLauncher lifecycle helpers ───────────────────────────────────

    override fun sessionIdFor(projectId: Uuid, agentId: String): Uuid? =
        sessions[projectId]?.get(agentId)

    override fun notifySessionExited(projectId: Uuid, sessionId: Uuid) {
        val forProject = sessions[projectId] ?: return
        val agentId = forProject.entries.firstOrNull { it.value == sessionId }?.key ?: return
        forProject.remove(agentId)
        if (forProject.isEmpty()) sessions.remove(projectId)
        _runningByProject.update { current ->
            val remaining = (current[projectId] ?: emptySet()) - agentId
            if (remaining.isEmpty()) current - projectId
            else current + (projectId to remaining)
        }
    }

    override fun removeProject(projectId: Uuid) {
        sessions.remove(projectId)
        _runningByProject.update { it - projectId }
    }

    override var onResolveEnvVar: ((String) -> String?)? = null

    // ── Test helpers ──────────────────────────────────────────────────────────

    /** Manually register a running session (simulates an already-running agent). */
    fun setRunning(projectId: Uuid, agentId: String, sessionId: Uuid) {
        sessions.getOrPut(projectId) { mutableMapOf() }[agentId] = sessionId
        _runningByProject.update { current ->
            current + (projectId to ((current[projectId] ?: emptySet()) + agentId))
        }
    }
}

package studio.vibe.shared.testutil

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import studio.vibe.shared.core.common.AIAgent
import studio.vibe.shared.core.common.terminal.TerminalSessionEvent
import studio.vibe.shared.core.common.terminal.TerminalSessionManaging
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.core.common.terminal.SplitDirection
import studio.vibe.shared.core.common.terminal.TabActivityState
import studio.vibe.shared.core.common.terminal.TerminalSession
import studio.vibe.shared.core.common.terminal.TerminalSessionState
import studio.vibe.shared.core.common.terminal.TerminalSize
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * In-memory [TerminalSessionManaging] used by ViewModel unit tests.
 *
 * Records every call so tests can verify behavior without spinning up a real
 * PTY process.  Emits [TerminalSessionEvent] through [emitEvent] so tests can
 * simulate process exits / activity.
 */
@OptIn(ExperimentalUuidApi::class)
class FakeTerminalSessionManaging : TerminalSessionManaging {

    private val _sessionsByProject =
        MutableStateFlow<Map<Uuid, List<TerminalSession>>>(emptyMap())
    override val sessionsByProject: StateFlow<Map<Uuid, List<TerminalSession>>> =
        _sessionsByProject.asStateFlow()

    private val _projectActivityStates =
        MutableStateFlow<Map<Uuid, TabActivityState>>(emptyMap())
    override val projectActivityStates: StateFlow<Map<Uuid, TabActivityState>> =
        _projectActivityStates.asStateFlow()

    private val _sessionEvents = MutableSharedFlow<TerminalSessionEvent>(extraBufferCapacity = 16)
    override val sessionEvents: Flow<TerminalSessionEvent> = _sessionEvents.asSharedFlow()

    /** Result used by [startAgentSession]; default success. */
    var startAgentSessionResult: Result<TerminalSession>? = null

    val createCallCount: Int get() = createCalls.size
    val startAgentCallCount: Int get() = startAgentCalls.size
    val killCallCount: Int get() = killCalls.size
    val sendInputCalls: MutableList<Pair<String, Uuid>> = mutableListOf()
    val createCalls: MutableList<Uuid> = mutableListOf()
    val startAgentCalls: MutableList<Triple<AIAgent, Uuid, String>> = mutableListOf()
    val killCalls: MutableList<Pair<Uuid, Boolean>> = mutableListOf()

    /** The [AIAgent] passed to the most recent [startAgentSession] call, or null if never called. */
    val lastStartedAgent: AIAgent? get() = startAgentCalls.lastOrNull()?.first

    fun emitEvent(event: TerminalSessionEvent) {
        _sessionEvents.tryEmit(event)
    }

    fun registerSession(session: TerminalSession) {
        _sessionsByProject.update { snapshot ->
            snapshot.toMutableMap().apply {
                val current = this[session.projectId] ?: emptyList()
                this[session.projectId] = current + session
            }
        }
    }

    override fun createSession(
        projectId: Uuid,
        shell: String?,
        workingDirectory: FilePath?,
        size: TerminalSize,
    ): TerminalSession {
        val session = TerminalSession(
            projectId = projectId,
            title = "fake",
            state = TerminalSessionState.Running,
        )
        createCalls.add(projectId)
        registerSession(session)
        return session
    }

    override fun resize(sessionId: Uuid, size: TerminalSize) = Unit

    override fun killSession(sessionId: Uuid, force: Boolean) {
        killCalls.add(sessionId to force)
    }

    override fun killAllSessions(projectId: Uuid) {
        _sessionsByProject.value = _sessionsByProject.value - projectId
    }

    override fun split(
        sessionId: Uuid,
        direction: SplitDirection,
        size: TerminalSize,
    ): TerminalSession {
        val owning = _sessionsByProject.value.values.flatten().firstOrNull { it.id == sessionId }
            ?: error("Unknown session $sessionId in FakeTerminalSessionManaging.split")
        return createSession(projectId = owning.projectId, shell = null, workingDirectory = null, size = size)
    }

    override fun startAgentSession(
        agent: AIAgent,
        projectId: Uuid,
        workingDirectory: String,
        apiKeyValue: String?,
    ): Result<TerminalSession> {
        startAgentCalls.add(Triple(agent, projectId, workingDirectory))
        val result = startAgentSessionResult ?: Result.success(
            TerminalSession(
                projectId = projectId,
                title = agent.displayName,
                state = TerminalSessionState.Running,
                isAgentSession = true,
            ),
        )
        result.onSuccess { registerSession(it) }
        return result
    }

    override fun session(id: Uuid): TerminalSession? =
        _sessionsByProject.value.values.flatten().firstOrNull { it.id == id }

    override fun sessions(projectId: Uuid): List<TerminalSession> =
        _sessionsByProject.value[projectId] ?: emptyList()

    override fun markProjectSeen(projectId: Uuid) {
        _projectActivityStates.update { snapshot ->
            if (snapshot[projectId] == TabActivityState.RUNNING)
                snapshot + (projectId to TabActivityState.IDLE)
            else
                snapshot
        }
    }

    override fun sendInput(text: String, sessionId: Uuid) {
        sendInputCalls.add(text to sessionId)
    }

    override fun scrollbackContent(sessionId: Uuid): String? = null
}

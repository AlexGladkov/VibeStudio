package studio.vibe.shared.contract

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.SplitDirection
import studio.vibe.shared.model.TabActivityState
import studio.vibe.shared.model.TerminalSession
import studio.vibe.shared.model.TerminalSize
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Terminal session lifecycle events
@OptIn(ExperimentalUuidApi::class)
sealed class TerminalSessionEvent {
    data class ActivityDetected(val sessionId: Uuid, val projectId: Uuid) : TerminalSessionEvent()
    data class ProcessExited(val sessionId: Uuid, val projectId: Uuid, val exitCode: Int) : TerminalSessionEvent()
    data class TitleChanged(val sessionId: Uuid, val newTitle: String) : TerminalSessionEvent()
    data class Bell(val sessionId: Uuid) : TerminalSessionEvent()
}

// Session creation
@OptIn(ExperimentalUuidApi::class)
interface TerminalSessionCreating {
    fun createSession(
        projectId: Uuid,
        shell: String? = null,
        workingDirectory: FilePath? = null,
        size: TerminalSize = TerminalSize(columns = 80, rows = 24),
    ): TerminalSession

    fun resize(sessionId: Uuid, size: TerminalSize)

    fun killSession(sessionId: Uuid, force: Boolean = false)

    fun killAllSessions(projectId: Uuid)

    fun split(
        sessionId: Uuid,
        direction: SplitDirection,
        size: TerminalSize = TerminalSize(columns = 80, rows = 24),
    ): TerminalSession

    /**
     * Spawn a new agent CLI session for [agent] inside [projectId].
     *
     * Failures (binary missing, PTY creation failed, session-limit hit, etc.)
     * are wrapped in [Result.failure] so callers can surface the original
     * exception's message to the user. Earlier returns of plain `null` made
     * every failure look identical from the UI.
     */
    fun startAgentSession(
        agent: AIAgent,
        projectId: Uuid,
        workingDirectory: String,
        apiKeyValue: String?,
    ): Result<TerminalSession>
}

// Session querying
@OptIn(ExperimentalUuidApi::class)
interface TerminalSessionQuerying {
    val sessionsByProject: StateFlow<Map<Uuid, List<TerminalSession>>>
    val projectActivityStates: StateFlow<Map<Uuid, TabActivityState>>
    fun session(id: Uuid): TerminalSession?
    fun sessions(projectId: Uuid): List<TerminalSession>
    val sessionEvents: Flow<TerminalSessionEvent>
    fun markProjectSeen(projectId: Uuid)
}

// Input sending
@OptIn(ExperimentalUuidApi::class)
interface TerminalInputSending {
    fun sendInput(text: String, sessionId: Uuid)
}

// Scrollback access
@OptIn(ExperimentalUuidApi::class)
interface TerminalScrollbackAccessing {
    fun scrollbackContent(sessionId: Uuid): String?
}

// Unified interface
@OptIn(ExperimentalUuidApi::class)
interface TerminalSessionManaging :
    TerminalSessionCreating,
    TerminalSessionQuerying,
    TerminalInputSending,
    TerminalScrollbackAccessing

/**
 * Minimal terminal surface needed by the Remote Control layer.
 *
 * By depending on this interface instead of the concrete [DesktopTerminalService],
 * [RemoteControlServer] and [RemoteSessionBridge] remain portable across targets
 * (desktop JVM, future macOS native). Implementations must be thread-safe.
 *
 * [outputFlow] returns a hot [kotlinx.coroutines.flow.Flow] of raw PTY output
 * chunks for the given session, or `null` if the session does not exist.
 */
@OptIn(ExperimentalUuidApi::class)
interface TerminalRemoteHost :
    TerminalSessionQuerying,
    TerminalInputSending {

    /**
     * Returns a hot [kotlinx.coroutines.flow.Flow] of raw PTY output strings
     * for [sessionId], or `null` when the session is not found.
     */
    fun outputFlow(sessionId: Uuid): kotlinx.coroutines.flow.Flow<String>?

    fun resize(sessionId: Uuid, size: TerminalSize)
}

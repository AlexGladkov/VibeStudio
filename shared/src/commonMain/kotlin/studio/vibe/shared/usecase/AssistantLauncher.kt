@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.usecase

import kotlinx.coroutines.flow.StateFlow
import studio.vibe.shared.model.TerminalSession
import kotlin.uuid.Uuid

/**
 * Encapsulates the information required to resume an existing native agent session.
 *
 * @param nativeSessionId The agent-assigned session UUID (e.g. Claude's session_id
 *   extracted from `{"type":"system","subtype":"init","session_id":"..."}` output).
 */
data class ResumeRequest(val nativeSessionId: String)

/**
 * Domain interface for starting and stopping AI agents.
 *
 * Extracted from [ToolbarViewModel] so that [RemoteControlServer] can trigger
 * the same logic without depending on the UI ViewModel layer.
 *
 * Implementations must be thread-safe. All mutations to [runningByProject] must
 * go through [StateFlow.value] via atomic updates — no plain mutable maps.
 */
interface AssistantLauncher {

    /**
     * Live map of project → set of running agent IDs.
     * Emits on every start/stop. Never null; empty map when no agents are running.
     */
    val runningByProject: StateFlow<Map<Uuid, Set<String>>>

    /**
     * Start the agent identified by [agentId] in the project [projectId].
     *
     * When [resume] is non-null and the agent supports session resumption, the
     * native agent is launched with the resume arguments appended so it continues
     * the previous conversation.
     *
     * @return [Result.success] with the created [TerminalSession] on success,
     *         or [Result.failure] with a descriptive exception on error.
     */
    suspend fun start(
        projectId: Uuid,
        agentId: String,
        resume: ResumeRequest? = null,
    ): Result<TerminalSession>

    /**
     * Stop the running agent identified by [agentId] in the project [projectId].
     *
     * No-op if the agent is not currently running in that project.
     */
    suspend fun stop(projectId: Uuid, agentId: String): Result<Unit>

    /**
     * Returns the session UUID for a currently running agent, or null if not running.
     *
     * Used by [ToolbarViewModel] to populate [ToolbarState.activeAgentSessionId].
     */
    fun sessionIdFor(projectId: Uuid, agentId: String): Uuid?

    /**
     * Notifies the launcher that a PTY process has exited so internal state stays
     * consistent with actual process state.
     *
     * Should be called from the [TerminalSessionManaging.sessionEvents] collector
     * on [TerminalSessionEvent.ProcessExited].
     */
    fun notifySessionExited(projectId: Uuid, sessionId: Uuid)

    /**
     * Cleans up all tracked state for [projectId] when a project is removed.
     */
    fun removeProject(projectId: Uuid)

    /**
     * Optional platform hook for resolving environment variables (e.g. process env on JVM).
     *
     * When set, the launcher uses this before falling back to [APIKeyResolving].
     * Default: null (no override).
     */
    var onResolveEnvVar: ((String) -> String?)?
}

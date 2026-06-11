@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.core.common.assistant

import kotlinx.coroutines.flow.StateFlow
import studio.vibe.shared.core.common.terminal.TerminalSession
import studio.vibe.shared.core.common.assistant.ResumeRequest
import kotlin.uuid.Uuid

/**
 * Domain service interface for starting and stopping AI agents.
 *
 * This is a domain service (not a UseCase) because it maintains live observable
 * state ([runningByProject]) and is shared between multiple callers
 * (ToolbarViewModel, RemoteControlServer). Use [StartAssistantUseCase] /
 * [StopAssistantUseCase] when you only need fire-and-forget start/stop without
 * holding a reference to the service itself.
 *
 * Implementations must be thread-safe. All mutations to [runningByProject] must
 * go through [StateFlow.value] via atomic updates — no plain mutable maps.
 */
interface AssistantLaunching {

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

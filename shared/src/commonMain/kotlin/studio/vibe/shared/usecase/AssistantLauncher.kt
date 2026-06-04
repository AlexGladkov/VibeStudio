@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.usecase

import kotlinx.coroutines.flow.StateFlow
import studio.vibe.shared.model.TerminalSession
import kotlin.uuid.Uuid

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
     * @return [Result.success] with the created [TerminalSession] on success,
     *         or [Result.failure] with a descriptive exception on error.
     */
    suspend fun start(projectId: Uuid, agentId: String): Result<TerminalSession>

    /**
     * Stop the running agent identified by [agentId] in the project [projectId].
     *
     * No-op if the agent is not currently running in that project.
     */
    suspend fun stop(projectId: Uuid, agentId: String): Result<Unit>
}

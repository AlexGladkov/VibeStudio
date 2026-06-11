@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.core.common

import kotlin.uuid.Uuid

/** Parameters for [ListRecentAgentSessionsUseCase]. */
data class ListRecentAgentSessionsParams(
    val projectId: Uuid,
    val limit: Int = 20,
)

/**
 * Returns the most-recently-started agent sessions for a given project.
 *
 * Results are ordered by [AgentSessionRecord.startedAt] descending (newest first)
 * and limited to [limit] entries.  This is the primary query used by the UI to
 * populate the session-resume picker.
 */
class ListRecentAgentSessionsUseCase(
    private val agentSessionLog: AgentSessionLog,
) : UseCase<ListRecentAgentSessionsParams, List<AgentSessionRecord>> {
    /**
     * Fetch up to [params.limit] recent sessions for [params.projectId].
     *
     * Never throws — exceptions are surfaced as [Result.failure].
     */
    override suspend fun invoke(params: ListRecentAgentSessionsParams): Result<List<AgentSessionRecord>> =
        runCatching {
            agentSessionLog.recentForProject(params.projectId.toString(), params.limit)
        }
}

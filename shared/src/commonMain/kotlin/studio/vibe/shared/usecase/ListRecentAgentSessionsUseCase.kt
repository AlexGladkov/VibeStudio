@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.usecase

import studio.vibe.shared.contract.AgentSessionLog
import studio.vibe.shared.contract.AgentSessionRecord
import kotlin.uuid.Uuid

/**
 * Returns the most-recently-started agent sessions for a given project.
 *
 * Results are ordered by [AgentSessionRecord.startedAt] descending (newest first)
 * and limited to [limit] entries.  This is the primary query used by the UI to
 * populate the session-resume picker.
 */
class ListRecentAgentSessionsUseCase(
    private val agentSessionLog: AgentSessionLog,
) {
    /**
     * Fetch up to [limit] recent sessions for [projectId].
     *
     * @param projectId VibeStudio project UUID.
     * @param limit     Maximum number of records to return. Defaults to 20.
     * @return Sorted list of [AgentSessionRecord], newest first.
     */
    suspend fun execute(projectId: Uuid, limit: Int = 20): List<AgentSessionRecord> =
        agentSessionLog.recentForProject(projectId.toString(), limit)
}

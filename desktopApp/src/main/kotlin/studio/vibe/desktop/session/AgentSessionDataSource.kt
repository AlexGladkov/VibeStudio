package studio.vibe.desktop.session

import kotlinx.coroutines.flow.Flow
import studio.vibe.shared.contract.AgentSessionRecord
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Contract for reading recent agent session records in the desktop UI.
 *
 * Production implementation is [KmpAgentSessionDataSource], which delegates to
 * [studio.vibe.shared.contract.AgentSessionLog].
 *
 * Test / stub implementation is [FakeAgentSessionDataSource].
 */
@OptIn(ExperimentalUuidApi::class)
interface AgentSessionDataSource {

    /**
     * Emits the most-recent [limit] sessions for [projectId] ordered by
     * [AgentSessionRecord.startedAt] descending.
     */
    fun recentSessionsFor(projectId: Uuid, limit: Int = 5): Flow<List<AgentSessionRecord>>
}

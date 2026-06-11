package studio.vibe.desktop.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import studio.vibe.shared.core.common.AgentSessionLog
import studio.vibe.shared.core.common.AgentSessionRecord
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Production [AgentSessionDataSource] backed by the KMP [AgentSessionLog].
 *
 * [recentSessionsFor] is a cold [Flow] — it re-queries on each collection.
 * Suitable for one-shot UI loads; upgrade to a hot StateFlow if live updates
 * become necessary.
 *
 * @param agentSessionLog KMP session log implementation.
 */
@OptIn(ExperimentalUuidApi::class)
class KmpAgentSessionDataSource(
    private val agentSessionLog: AgentSessionLog,
) : AgentSessionDataSource {

    override fun recentSessionsFor(projectId: Uuid, limit: Int): Flow<List<AgentSessionRecord>> =
        flow {
            emit(agentSessionLog.recentForProject(projectId.toString(), limit))
        }
}

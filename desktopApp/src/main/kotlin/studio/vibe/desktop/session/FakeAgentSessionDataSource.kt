package studio.vibe.desktop.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import studio.vibe.shared.core.common.AgentSessionRecord
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * In-memory stub for [AgentSessionDataSource].
 *
 * Returns an empty list for all queries so the UI renders its empty states
 * while the real [KmpAgentSessionDataSource] is not yet injected or when running in tests.
 *
 * Thread-safety: [_sessions] is a [MutableStateFlow]; all mutations are atomic.
 */
@OptIn(ExperimentalUuidApi::class)
class FakeAgentSessionDataSource : AgentSessionDataSource {

    private val _sessions = MutableStateFlow<List<AgentSessionRecord>>(emptyList())

    override fun recentSessionsFor(projectId: Uuid, limit: Int): Flow<List<AgentSessionRecord>> =
        _sessions.map { all ->
            all.filter { it.projectId == projectId.toString() }
                .sortedByDescending { it.startedAt }
                .take(limit)
        }
}

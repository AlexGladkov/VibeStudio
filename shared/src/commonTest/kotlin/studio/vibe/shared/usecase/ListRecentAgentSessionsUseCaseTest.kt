@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.session.domain.usecase

import kotlinx.coroutines.test.runTest
import studio.vibe.shared.core.common.AgentSessionRecord
import studio.vibe.shared.core.common.ListRecentAgentSessionsParams
import studio.vibe.shared.core.common.ListRecentAgentSessionsUseCase
import studio.vibe.shared.testutil.FakeAgentSessionLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ListRecentAgentSessionsUseCaseTest {

    private val log = FakeAgentSessionLog()
    private val useCase = ListRecentAgentSessionsUseCase(log)

    private val projectId: Uuid = Uuid.random()
    private val otherProjectId: Uuid = Uuid.random()

    private fun record(
        sessionId: String,
        projectId: Uuid = this.projectId,
        startedAt: Long,
    ) = AgentSessionRecord(
        sessionId = sessionId,
        projectId = projectId.toString(),
        agentId = "claude",
        startedAt = startedAt,
    )

    private suspend fun invoke(projectId: Uuid = this.projectId, limit: Int = 20): List<AgentSessionRecord> =
        useCase(ListRecentAgentSessionsParams(projectId, limit)).getOrElse { emptyList() }

    // ── Basic query ───────────────────────────────────────────────────────────

    @Test
    fun execute_emptyLog_returnsEmptyList() = runTest {
        val results = invoke()
        assertTrue(results.isEmpty())
    }

    @Test
    fun execute_singleSession_returnsSingleItem() = runTest {
        log.append(record("s1", startedAt = 1_000L))

        val results = invoke()
        assertEquals(1, results.size)
        assertEquals("s1", results[0].sessionId)
    }

    // ── Ordering ──────────────────────────────────────────────────────────────

    @Test
    fun execute_multipleSessionsSameProject_orderedNewestFirst() = runTest {
        log.append(record("early",  startedAt = 100L))
        log.append(record("latest", startedAt = 300L))
        log.append(record("middle", startedAt = 200L))

        val results = invoke()
        assertEquals(listOf("latest", "middle", "early"), results.map { it.sessionId })
    }

    @Test
    fun execute_sessionsInsertedInReverseOrder_stillOrderedNewestFirst() = runTest {
        log.append(record("newest", startedAt = 1_000L))
        log.append(record("older",  startedAt = 500L))
        log.append(record("oldest", startedAt = 100L))

        val results = invoke()
        assertEquals("newest", results[0].sessionId)
        assertEquals("oldest", results.last().sessionId)
    }

    // ── Limit ─────────────────────────────────────────────────────────────────

    @Test
    fun execute_limitApplied_returnsAtMostLimitRecords() = runTest {
        repeat(10) { i ->
            log.append(record("s$i", startedAt = i.toLong()))
        }

        val results = invoke(limit = 3)
        assertEquals(3, results.size)
    }

    @Test
    fun execute_limitApplied_returnsMostRecentOnes() = runTest {
        log.append(record("oldest",  startedAt = 100L))
        log.append(record("middle",  startedAt = 200L))
        log.append(record("newest",  startedAt = 300L))

        val results = invoke(limit = 2)
        assertEquals(listOf("newest", "middle"), results.map { it.sessionId })
    }

    @Test
    fun execute_limitLargerThanTotal_returnsAll() = runTest {
        log.append(record("s1", startedAt = 100L))
        log.append(record("s2", startedAt = 200L))

        val results = invoke(limit = 100)
        assertEquals(2, results.size)
    }

    // ── Project isolation ─────────────────────────────────────────────────────

    @Test
    fun execute_onlyReturnsSessionsForGivenProject() = runTest {
        log.append(record("mine",   projectId = projectId,      startedAt = 100L))
        log.append(record("theirs", projectId = otherProjectId, startedAt = 200L))
        log.append(record("mine2",  projectId = projectId,      startedAt = 300L))

        val results = invoke()
        assertEquals(2, results.size)
        assertTrue(results.all { it.projectId == projectId.toString() })
        assertTrue(results.none { it.sessionId == "theirs" })
    }

    @Test
    fun execute_otherProjectHasNoSessions_returnsEmpty() = runTest {
        log.append(record("s1", projectId = otherProjectId, startedAt = 100L))

        val results = invoke()
        assertTrue(results.isEmpty())
    }

    // ── Default limit ─────────────────────────────────────────────────────────

    @Test
    fun execute_defaultLimit_isAtLeast20() = runTest {
        repeat(25) { i ->
            log.append(record("s$i", startedAt = i.toLong()))
        }

        val results = invoke() // default limit = 20
        assertEquals(20, results.size)
    }
}

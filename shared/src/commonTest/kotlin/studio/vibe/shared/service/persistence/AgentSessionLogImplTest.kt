package studio.vibe.shared.service.persistence

import kotlinx.coroutines.test.runTest
import studio.vibe.shared.contract.AgentSessionRecord
import studio.vibe.shared.testutil.FakePersistenceStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentSessionLogImplTest {

    private fun buildLog(store: FakePersistenceStore = FakePersistenceStore()): AgentSessionLogImpl =
        AgentSessionLogImpl(persistence = store)

    private fun record(
        sessionId: String = "session-1",
        projectId: String = "project-A",
        agentId: String = "claude",
        startedAt: Long = 1_000L,
    ) = AgentSessionRecord(
        sessionId = sessionId,
        projectId = projectId,
        agentId = agentId,
        startedAt = startedAt,
    )

    // ── append ────────────────────────────────────────────────────────────────

    @Test
    fun append_singleRecord_canBeRetrievedById() = runTest {
        val log = buildLog()
        val rec = record()
        log.append(rec)

        val found = log.byId(rec.sessionId)
        assertNotNull(found)
        assertEquals(rec, found)
    }

    @Test
    fun append_multipleRecords_allStored() = runTest {
        val log = buildLog()
        log.append(record(sessionId = "s1", startedAt = 100L))
        log.append(record(sessionId = "s2", startedAt = 200L))
        log.append(record(sessionId = "s3", startedAt = 300L))

        assertNotNull(log.byId("s1"))
        assertNotNull(log.byId("s2"))
        assertNotNull(log.byId("s3"))
    }

    @Test
    fun append_sameSessionId_replacesExisting() = runTest {
        val log = buildLog()
        log.append(record(sessionId = "s1", agentId = "claude"))
        log.append(record(sessionId = "s1", agentId = "codex"))

        val found = log.byId("s1")
        assertNotNull(found)
        assertEquals("codex", found.agentId)
    }

    // ── persistence round-trip ────────────────────────────────────────────────

    @Test
    fun append_persistsToDisk_newInstanceReadsBack() = runTest {
        val store = FakePersistenceStore()
        val log1 = buildLog(store)
        val rec = record(sessionId = "persisted-1", agentId = "gemini")
        log1.append(rec)

        // Simulate app restart — fresh instance, same store
        val log2 = buildLog(store)
        val found = log2.byId("persisted-1")
        assertNotNull(found)
        assertEquals(rec, found)
    }

    @Test
    fun append_multipleSessions_roundTrip() = runTest {
        val store = FakePersistenceStore()
        val log1 = buildLog(store)
        log1.append(record(sessionId = "a", startedAt = 100L))
        log1.append(record(sessionId = "b", startedAt = 200L))
        log1.append(record(sessionId = "c", startedAt = 300L))

        val log2 = buildLog(store)
        assertNotNull(log2.byId("a"))
        assertNotNull(log2.byId("b"))
        assertNotNull(log2.byId("c"))
    }

    @Test
    fun persistence_malformedLine_skippedGracefully() = runTest {
        val store = FakePersistenceStore()
        // Write a valid record followed by a broken JSON line
        val log1 = buildLog(store)
        log1.append(record(sessionId = "good-1"))

        // Inject a corrupt line after the valid one
        val logFile = studio.vibe.shared.model.FilePath(
            "${store.appSupportDirectory().path}/agent-sessions.jsonl"
        )
        val existing = store.readFile(logFile)?.decodeToString() ?: ""
        store.writeFile(logFile, (existing + "\n{not valid json").encodeToByteArray())

        val log2 = buildLog(store)
        assertNotNull(log2.byId("good-1"), "valid record must survive malformed line")
    }

    // ── updateNativeSessionId ─────────────────────────────────────────────────

    @Test
    fun updateNativeSessionId_setsField() = runTest {
        val log = buildLog()
        log.append(record(sessionId = "s1"))
        log.updateNativeSessionId("s1", "native-abc-123")

        val found = log.byId("s1")
        assertNotNull(found)
        assertEquals("native-abc-123", found.agentNativeSessionId)
    }

    @Test
    fun updateNativeSessionId_unknownSession_isNoOp() = runTest {
        val log = buildLog()
        log.updateNativeSessionId("nonexistent", "native-id") // must not throw
        assertNull(log.byId("nonexistent"))
    }

    // ── updateFirstPrompt ─────────────────────────────────────────────────────

    @Test
    fun updateFirstPrompt_setsField() = runTest {
        val log = buildLog()
        log.append(record(sessionId = "s1"))
        log.updateFirstPrompt("s1", "Fix the null pointer crash")

        val found = log.byId("s1")
        assertNotNull(found)
        assertEquals("Fix the null pointer crash", found.firstPrompt)
    }

    @Test
    fun updateFirstPrompt_truncatesTo200Chars() = runTest {
        val log = buildLog()
        log.append(record(sessionId = "s1"))
        val longPrompt = "x".repeat(300)
        log.updateFirstPrompt("s1", longPrompt)

        val found = log.byId("s1")
        assertNotNull(found)
        assertEquals(200, found.firstPrompt?.length)
    }

    @Test
    fun updateFirstPrompt_unknownSession_isNoOp() = runTest {
        val log = buildLog()
        log.updateFirstPrompt("nonexistent", "anything")
        assertNull(log.byId("nonexistent"))
    }

    // ── updateSummary ─────────────────────────────────────────────────────────

    @Test
    fun updateSummary_setsField() = runTest {
        val log = buildLog()
        log.append(record(sessionId = "s1"))
        log.updateSummary("s1", "Fixed auth bug in login flow")

        val found = log.byId("s1")
        assertNotNull(found)
        assertEquals("Fixed auth bug in login flow", found.summary)
    }

    @Test
    fun updateSummary_unknownSession_isNoOp() = runTest {
        val log = buildLog()
        log.updateSummary("nonexistent", "summary text")
    }

    // ── markEnded ─────────────────────────────────────────────────────────────

    @Test
    fun markEnded_setsEndedAt() = runTest {
        val log = buildLog()
        log.append(record(sessionId = "s1"))
        log.markEnded("s1", endedAt = 9_999L)

        val found = log.byId("s1")
        assertNotNull(found)
        assertEquals(9_999L, found.endedAt)
    }

    @Test
    fun markEnded_unknownSession_isNoOp() = runTest {
        val log = buildLog()
        log.markEnded("nonexistent", endedAt = 1L)
    }

    // ── recentForProject ──────────────────────────────────────────────────────

    @Test
    fun recentForProject_returnsOnlyMatchingProject() = runTest {
        val log = buildLog()
        log.append(record(sessionId = "a", projectId = "proj-1", startedAt = 100L))
        log.append(record(sessionId = "b", projectId = "proj-2", startedAt = 200L))
        log.append(record(sessionId = "c", projectId = "proj-1", startedAt = 300L))

        val results = log.recentForProject("proj-1", limit = 20)
        assertEquals(2, results.size)
        assertTrue(results.all { it.projectId == "proj-1" })
    }

    @Test
    fun recentForProject_orderedByStartedAtDescending() = runTest {
        val log = buildLog()
        log.append(record(sessionId = "early",  projectId = "proj-1", startedAt = 100L))
        log.append(record(sessionId = "middle", projectId = "proj-1", startedAt = 200L))
        log.append(record(sessionId = "latest", projectId = "proj-1", startedAt = 300L))

        val results = log.recentForProject("proj-1", limit = 20)
        assertEquals(listOf("latest", "middle", "early"), results.map { it.sessionId })
    }

    @Test
    fun recentForProject_limitsResults() = runTest {
        val log = buildLog()
        repeat(10) { i ->
            log.append(record(sessionId = "s$i", projectId = "proj-1", startedAt = i.toLong()))
        }

        val results = log.recentForProject("proj-1", limit = 3)
        assertEquals(3, results.size)
    }

    @Test
    fun recentForProject_emptyProject_returnsEmpty() = runTest {
        val log = buildLog()
        log.append(record(sessionId = "s1", projectId = "other"))

        val results = log.recentForProject("proj-1", limit = 20)
        assertTrue(results.isEmpty())
    }

    @Test
    fun recentForProject_updatedRecords_reflectLatestState() = runTest {
        val log = buildLog()
        log.append(record(sessionId = "s1", projectId = "proj-1"))
        log.updateNativeSessionId("s1", "native-xyz")

        val results = log.recentForProject("proj-1", limit = 20)
        assertEquals(1, results.size)
        assertEquals("native-xyz", results[0].agentNativeSessionId)
    }

    // ── byId ──────────────────────────────────────────────────────────────────

    @Test
    fun byId_unknownId_returnsNull() = runTest {
        val log = buildLog()
        assertNull(log.byId("nonexistent"))
    }

    @Test
    fun byId_emptyLog_returnsNull() = runTest {
        val log = buildLog()
        assertNull(log.byId("any-id"))
    }
}

package studio.vibe.shared.testutil

import studio.vibe.shared.contract.AgentSessionLog
import studio.vibe.shared.contract.AgentSessionRecord

/**
 * In-memory [AgentSessionLog] test double.
 *
 * Stores records in a [LinkedHashMap] keyed by sessionId.  All operations are
 * synchronous and side-effect-free — no disk I/O.
 */
class FakeAgentSessionLog : AgentSessionLog {

    private val records = LinkedHashMap<String, AgentSessionRecord>()

    // ── Call recording ────────────────────────────────────────────────────────

    val appendCalls: MutableList<AgentSessionRecord> = mutableListOf()
    val updateNativeSessionIdCalls: MutableList<Pair<String, String>> = mutableListOf()
    val updateFirstPromptCalls: MutableList<Pair<String, String>> = mutableListOf()
    val updateSummaryCalls: MutableList<Pair<String, String>> = mutableListOf()
    val markEndedCalls: MutableList<String> = mutableListOf()

    // ── AgentSessionLog ───────────────────────────────────────────────────────

    override suspend fun append(record: AgentSessionRecord) {
        appendCalls.add(record)
        records[record.sessionId] = record
    }

    override suspend fun updateNativeSessionId(sessionId: String, nativeId: String) {
        updateNativeSessionIdCalls.add(sessionId to nativeId)
        val existing = records[sessionId] ?: return
        records[sessionId] = existing.copy(agentNativeSessionId = nativeId)
    }

    override suspend fun updateFirstPrompt(sessionId: String, prompt: String) {
        updateFirstPromptCalls.add(sessionId to prompt)
        val existing = records[sessionId] ?: return
        records[sessionId] = existing.copy(firstPrompt = prompt.take(200))
    }

    override suspend fun updateSummary(sessionId: String, summary: String) {
        updateSummaryCalls.add(sessionId to summary)
        val existing = records[sessionId] ?: return
        records[sessionId] = existing.copy(summary = summary)
    }

    override suspend fun markEnded(sessionId: String, endedAt: Long) {
        markEndedCalls.add(sessionId)
        val existing = records[sessionId] ?: return
        records[sessionId] = existing.copy(endedAt = endedAt)
    }

    override suspend fun recentForProject(projectId: String, limit: Int): List<AgentSessionRecord> =
        records.values
            .filter { it.projectId == projectId }
            .sortedByDescending { it.startedAt }
            .take(limit)

    override suspend fun byId(sessionId: String): AgentSessionRecord? = records[sessionId]

    // ── Test helpers ──────────────────────────────────────────────────────────

    /** All records currently in the store, in insertion order. */
    fun allRecords(): List<AgentSessionRecord> = records.values.toList()

    /** Number of records currently stored. */
    fun size(): Int = records.size

    /** Clear all records and call history. */
    fun reset() {
        records.clear()
        appendCalls.clear()
        updateNativeSessionIdCalls.clear()
        updateFirstPromptCalls.clear()
        updateSummaryCalls.clear()
        markEndedCalls.clear()
    }
}

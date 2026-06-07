package studio.vibe.shared.service.persistence

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import studio.vibe.shared.contract.AgentSessionLog
import studio.vibe.shared.contract.AgentSessionRecord
import studio.vibe.shared.contract.PersistenceStore
import studio.vibe.shared.model.FilePath
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * JSONL append-only implementation of [AgentSessionLog].
 *
 * Storage: `<storageDirectory>/agent-sessions.jsonl` — one JSON object per line.
 *
 * Write strategy: read all → merge in-memory → rewrite atomically (via a temp
 * file that the [PersistenceStore] overwrites in place).  This guarantees no
 * torn writes at the cost of O(n) per update — acceptable for files < 100 KB
 * (≈ 2 000 sessions before any pruning).
 *
 * Thread safety: all mutations are serialised through [mutex].  Reads that only
 * consult the in-memory cache are also guarded by the same lock so callers
 * never observe a partial write.
 *
 * @param persistence      Platform file I/O abstraction.
 * @param storageDirectory Directory in which `agent-sessions.jsonl` lives.
 *   Defaults to [PersistenceStore.appSupportDirectory].
 */
@OptIn(ExperimentalTime::class)
class AgentSessionLogImpl(
    private val persistence: PersistenceStore,
    private val storageDirectory: FilePath? = null,
) : AgentSessionLog {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val mutex = Mutex()

    /** Lazily-loaded, in-memory view of all records keyed by sessionId. */
    private var cache: LinkedHashMap<String, AgentSessionRecord>? = null

    // ── Internal directory / file paths ───────────────────────────────────────

    private fun dir(): FilePath = storageDirectory ?: persistence.appSupportDirectory()

    private fun logFile(): FilePath = FilePath("${dir().path}/agent-sessions.jsonl")

    // ── Cache management ──────────────────────────────────────────────────────

    /**
     * Load (or return the cached) record map.  Must only be called while [mutex] is held.
     */
    private suspend fun loadCache(): LinkedHashMap<String, AgentSessionRecord> {
        cache?.let { return it }

        val bytes = persistence.readFile(logFile())
        val loaded = LinkedHashMap<String, AgentSessionRecord>()

        if (bytes != null) {
            bytes.decodeToString()
                .lineSequence()
                .filter { it.isNotBlank() }
                .forEach { line ->
                    try {
                        val record = json.decodeFromString<AgentSessionRecord>(line)
                        loaded[record.sessionId] = record
                    } catch (_: Exception) {
                        // Skip malformed lines — resilience over correctness for log data.
                    }
                }
        }

        cache = loaded
        return loaded
    }

    /**
     * Flush the current [cache] to disk.  Must only be called while [mutex] is held
     * and [cache] must already be non-null.
     */
    private suspend fun flushCache() {
        val records = cache ?: return
        val text = records.values.joinToString("\n") { json.encodeToString(it) }
        persistence.writeFile(logFile(), text.encodeToByteArray())
    }

    // ── AgentSessionLog ───────────────────────────────────────────────────────

    override suspend fun append(record: AgentSessionRecord) {
        mutex.withLock {
            val c = loadCache()
            c[record.sessionId] = record
            flushCache()
        }
    }

    override suspend fun updateNativeSessionId(sessionId: String, nativeId: String) {
        mutex.withLock {
            val c = loadCache()
            val existing = c[sessionId] ?: return@withLock
            c[sessionId] = existing.copy(agentNativeSessionId = nativeId)
            flushCache()
        }
    }

    override suspend fun updateFirstPrompt(sessionId: String, prompt: String) {
        mutex.withLock {
            val c = loadCache()
            val existing = c[sessionId] ?: return@withLock
            c[sessionId] = existing.copy(firstPrompt = prompt.take(200))
            flushCache()
        }
    }

    override suspend fun updateSummary(sessionId: String, summary: String) {
        mutex.withLock {
            val c = loadCache()
            val existing = c[sessionId] ?: return@withLock
            c[sessionId] = existing.copy(summary = summary)
            flushCache()
        }
    }

    override suspend fun markEnded(sessionId: String, endedAt: Long) {
        mutex.withLock {
            val c = loadCache()
            val existing = c[sessionId] ?: return@withLock
            c[sessionId] = existing.copy(endedAt = endedAt)
            flushCache()
        }
    }

    override suspend fun recentForProject(
        projectId: String,
        limit: Int,
    ): List<AgentSessionRecord> {
        mutex.withLock {
            val c = loadCache()
            return c.values
                .filter { it.projectId == projectId }
                .sortedByDescending { it.startedAt }
                .take(limit)
        }
    }

    override suspend fun byId(sessionId: String): AgentSessionRecord? {
        mutex.withLock {
            return loadCache()[sessionId]
        }
    }
}

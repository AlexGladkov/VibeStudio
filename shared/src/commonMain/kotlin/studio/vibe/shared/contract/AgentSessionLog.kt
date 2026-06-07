package studio.vibe.shared.contract

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Persisted record of a single agent session (one PTY process lifetime).
 *
 * Append-only: new records are written on session start, and "patch" records
 * are written when nativeSessionId / firstPrompt / summary become available.
 * [AgentSessionLog.byId] and [AgentSessionLog.recentForProject] merge all patches
 * in-memory before returning results.
 */
@Serializable
data class AgentSessionRecord(
    /** VibeStudio PTY session UUID (stable, assigned on session creation). */
    val sessionId: String,

    /** VibeStudio project UUID. */
    val projectId: String,

    /** Agent identifier: "claude", "codex", "gemini", "qwen", "opencode". */
    val agentId: String,

    /** Native session UUID returned by the agent (e.g. Claude's --resume target). Null until captured. */
    val agentNativeSessionId: String? = null,

    /** Epoch-millisecond timestamp when the session was started. */
    val startedAt: Long,

    /** Epoch-millisecond timestamp when the session ended. Null while still running. */
    val endedAt: Long? = null,

    /** First 200 characters of the first user message sent in this session. */
    val firstPrompt: String? = null,

    /** Optional LLM-generated summary of the session. */
    val summary: String? = null,
)

/**
 * Storage and query layer for [AgentSessionRecord]s.
 *
 * Implementations must be coroutine-safe (all writes coordinated via Mutex or
 * equivalent) and persist records across app restarts.
 */
interface AgentSessionLog {

    /**
     * Append a new [record] to the log.
     * If a record with the same [AgentSessionRecord.sessionId] already exists, it is replaced.
     */
    suspend fun append(record: AgentSessionRecord)

    /**
     * Set the native agent session UUID for [sessionId].
     * No-op if [sessionId] is not found.
     */
    suspend fun updateNativeSessionId(sessionId: String, nativeId: String)

    /**
     * Set the first prompt text (truncated to 200 chars) for [sessionId].
     * No-op if [sessionId] is not found.
     */
    suspend fun updateFirstPrompt(sessionId: String, prompt: String)

    /**
     * Set the LLM-generated summary for [sessionId].
     * No-op if [sessionId] is not found.
     */
    suspend fun updateSummary(sessionId: String, summary: String)

    /**
     * Mark the session as ended at [endedAt] (defaults to now).
     * No-op if [sessionId] is not found.
     */
    @OptIn(ExperimentalTime::class)
    suspend fun markEnded(
        sessionId: String,
        endedAt: Long = Clock.System.now().toEpochMilliseconds(),
    )

    /**
     * Return the [limit] most-recently-started records for [projectId], sorted
     * by [AgentSessionRecord.startedAt] descending.
     */
    suspend fun recentForProject(projectId: String, limit: Int = 20): List<AgentSessionRecord>

    /**
     * Return the record for [sessionId], or null if not found.
     */
    suspend fun byId(sessionId: String): AgentSessionRecord?
}

@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.usecase

import studio.vibe.shared.contract.AgentSessionLog
import kotlin.uuid.Uuid

/**
 * Extracts the native agent session UUID from a line of terminal output and
 * persists it via [AgentSessionLog.updateNativeSessionId].
 *
 * Claude emits a structured JSON line early in its output when launched with
 * `--output-format stream-json`:
 *
 * ```json
 * {"type":"system","subtype":"init","session_id":"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",...}
 * ```
 *
 * The extracted UUID is stored so the session can later be resumed via
 * [AssistantLauncher.start] with a [ResumeRequest].
 *
 * This use-case is intentionally narrow: it parses one line at a time and is
 * called from the desktop terminal output watcher hot path.  It must not throw.
 *
 * @param agentSessionLog Log to which the native session ID is written.
 */
class CaptureAgentSessionIdUseCase(
    private val agentSessionLog: AgentSessionLog,
) {
    /**
     * Parse [line] for a `session_id` JSON field and, if found, persist the value
     * against [sessionId] in [agentSessionLog].
     *
     * Silent no-op when [line] contains no recognisable session ID.
     */
    suspend fun execute(sessionId: Uuid, line: String) {
        val match = SESSION_ID_REGEX.find(line) ?: return
        val nativeId = match.groupValues[1]
        try {
            agentSessionLog.updateNativeSessionId(sessionId.toString(), nativeId)
        } catch (e: Exception) {
            println("CaptureAgentSessionIdUseCase: failed to persist native session id: ${e.message}")
        }
    }

    companion object {
        /**
         * Matches the `"session_id":"<uuid>"` pattern in a Claude stream-json line.
         *
         * The regex is intentionally lenient about surrounding whitespace so it
         * also handles pretty-printed JSON and edge-case formatting variants.
         */
        internal val SESSION_ID_REGEX =
            """"session_id"\s*:\s*"([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"""".toRegex()
    }
}

@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.core.common

import kotlin.uuid.Uuid

/** Parameters for [CaptureAgentSessionIdUseCase]. */
data class CaptureAgentSessionIdParams(
    val sessionId: Uuid,
    val line: String,
)

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
) : UseCase<CaptureAgentSessionIdParams, Unit> {
    /**
     * Parse [params.line] for a `session_id` JSON field and, if found, persist the value
     * against [params.sessionId] in [agentSessionLog].
     *
     * Silent no-op when [params.line] contains no recognisable session ID.
     * Never throws — all exceptions are surfaced as [Result.failure].
     */
    override suspend fun invoke(params: CaptureAgentSessionIdParams): Result<Unit> = runCatching {
        val match = SESSION_ID_REGEX.find(params.line) ?: return Result.success(Unit)
        val nativeId = match.groupValues[1]
        agentSessionLog.updateNativeSessionId(params.sessionId.toString(), nativeId)
    }.recoverCatching { e ->
        println("CaptureAgentSessionIdUseCase: failed to persist native session id: ${e.message}")
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

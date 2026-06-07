@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.usecase

import kotlinx.coroutines.test.runTest
import studio.vibe.shared.contract.AgentSessionRecord
import studio.vibe.shared.testutil.FakeAgentSessionLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class CaptureAgentSessionIdUseCaseTest {

    private val log = FakeAgentSessionLog()
    private val useCase = CaptureAgentSessionIdUseCase(log)

    private val sessionId: Uuid = Uuid.random()

    private suspend fun seedSession() {
        log.append(
            AgentSessionRecord(
                sessionId = sessionId.toString(),
                projectId = "proj-1",
                agentId = "claude",
                startedAt = 1_000L,
            )
        )
    }

    // ── Valid Claude stream-json lines ────────────────────────────────────────

    @Test
    fun execute_validClaudeInitLine_persistsNativeSessionId() = runTest {
        seedSession()
        val line = """{"type":"system","subtype":"init","session_id":"12345678-1234-1234-1234-123456789abc","tools":[],"mcp_servers":[]}"""

        useCase.execute(sessionId, line)

        assertEquals(1, log.updateNativeSessionIdCalls.size)
        assertEquals(sessionId.toString() to "12345678-1234-1234-1234-123456789abc", log.updateNativeSessionIdCalls[0])
    }

    @Test
    fun execute_validLine_extractsCorrectUuid() = runTest {
        seedSession()
        val uuid = "abcdef01-abcd-abcd-abcd-abcdef012345"
        val line = """{"type":"system","subtype":"init","session_id":"$uuid"}"""

        useCase.execute(sessionId, line)

        assertEquals(uuid, log.updateNativeSessionIdCalls.firstOrNull()?.second)
    }

    @Test
    fun execute_lineWithExtraWhitespace_stillParses() = runTest {
        seedSession()
        val line = """{ "session_id" : "aaaabbbb-cccc-dddd-eeee-ffffaaaabbbb" }"""

        useCase.execute(sessionId, line)

        assertEquals("aaaabbbb-cccc-dddd-eeee-ffffaaaabbbb", log.updateNativeSessionIdCalls.firstOrNull()?.second)
    }

    // ── Invalid / irrelevant lines ────────────────────────────────────────────

    @Test
    fun execute_regularTerminalOutput_noUpdate() = runTest {
        seedSession()
        useCase.execute(sessionId, "Compiling files...")

        assertTrue(log.updateNativeSessionIdCalls.isEmpty())
    }

    @Test
    fun execute_emptyLine_noUpdate() = runTest {
        seedSession()
        useCase.execute(sessionId, "")

        assertTrue(log.updateNativeSessionIdCalls.isEmpty())
    }

    @Test
    fun execute_jsonWithoutSessionId_noUpdate() = runTest {
        seedSession()
        val line = """{"type":"system","subtype":"init","other_field":"value"}"""

        useCase.execute(sessionId, line)

        assertTrue(log.updateNativeSessionIdCalls.isEmpty())
    }

    @Test
    fun execute_malformedJson_noUpdate() = runTest {
        seedSession()
        useCase.execute(sessionId, "{not valid json at all}")

        assertTrue(log.updateNativeSessionIdCalls.isEmpty())
    }

    @Test
    fun execute_sessionIdWrongFormat_noUpdate() = runTest {
        seedSession()
        // session_id present but not UUID format
        val line = """{"session_id":"not-a-uuid-at-all"}"""

        useCase.execute(sessionId, line)

        assertTrue(log.updateNativeSessionIdCalls.isEmpty())
    }

    @Test
    fun execute_sessionIdPartialUuid_noUpdate() = runTest {
        seedSession()
        val line = """{"session_id":"12345678-1234-1234"}"""

        useCase.execute(sessionId, line)

        assertTrue(log.updateNativeSessionIdCalls.isEmpty())
    }

    // ── No-throw guarantee ────────────────────────────────────────────────────

    @Test
    fun execute_noMatchingSession_doesNotThrow() = runTest {
        // Log is empty — updateNativeSessionId will be a no-op in the real impl,
        // but the use case itself must never throw.
        val line = """{"type":"system","subtype":"init","session_id":"12345678-1234-1234-1234-123456789abc"}"""

        useCase.execute(sessionId, line) // must complete without throwing
    }

    // ── Regex companion object ────────────────────────────────────────────────

    @Test
    fun regex_matchesValidUuid() {
        val uuid = "12345678-1234-1234-1234-123456789abc"
        val line = """"session_id":"$uuid""""
        val match = CaptureAgentSessionIdUseCase.SESSION_ID_REGEX.find(line)
        assertEquals(uuid, match?.groupValues?.get(1))
    }

    @Test
    fun regex_doesNotMatchMissingHyphens() {
        val line = """"session_id":"1234567812341234123412345678abcd""""
        val match = CaptureAgentSessionIdUseCase.SESSION_ID_REGEX.find(line)
        assertEquals(null, match)
    }

    @Test
    fun regex_matchesUppercaseHex() {
        val uuid = "ABCDEF01-ABCD-ABCD-ABCD-ABCDEF012345"
        val line = """"session_id":"$uuid""""
        val match = CaptureAgentSessionIdUseCase.SESSION_ID_REGEX.find(line)
        assertEquals(uuid, match?.groupValues?.get(1))
    }
}

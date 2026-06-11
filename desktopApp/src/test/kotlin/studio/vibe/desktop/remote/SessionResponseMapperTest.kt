@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.remote

import studio.vibe.shared.core.common.terminal.TerminalSession
import studio.vibe.shared.core.common.terminal.TerminalSessionState
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Unit tests for [SessionResponseMapper].
 *
 * No mocking required: [SessionResponseMapper] is a stateless object; all
 * collaborators ([TerminalSession], [RemoteSessionBridge]) are plain data.
 *
 * Covered scenarios:
 * 1. Full [TerminalSession] → [SessionResponse] round-trip
 * 2. Null / empty optional fields are handled gracefully
 * 3. All [TerminalSessionState] enum variants map to the correct string
 */
class SessionResponseMapperTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeSession(
        state: TerminalSessionState = TerminalSessionState.Running,
        title: String = "zsh",
        isAgentSession: Boolean = false,
    ): TerminalSession = TerminalSession(
        projectId = Uuid.random(),
        title = title,
        state = state,
        isAgentSession = isAgentSession,
    )

    // ── 1. Full mapping round-trip ────────────────────────────────────────────

    @Test
    fun `map returns SessionResponse with correct id, title, and state`() {
        val session = makeSession(title = "Claude Agent")
        val response = SessionResponseMapper.map(session) { emptyList() }

        assertEquals(session.id.toString(), response.id)
        assertEquals("Claude Agent", response.title)
        assertEquals("running", response.state)
    }

    @Test
    fun `map sets isAgent to true for agent sessions`() {
        val session = makeSession(isAgentSession = true)
        val response = SessionResponseMapper.map(session) { emptyList() }
        assertTrue(response.isAgent)
    }

    @Test
    fun `map sets isAgent to false for non-agent sessions`() {
        val session = makeSession(isAgentSession = false)
        val response = SessionResponseMapper.map(session) { emptyList() }
        assertFalse(response.isAgent)
    }

    @Test
    fun `map sets hasRemoteAttachment true when bridge exists for session`() {
        val session = makeSession()
        val sessionUuid = UUID.fromString(session.id.toString())

        val bridge = buildFakeBridge(sessionId = sessionUuid)
        val response = SessionResponseMapper.map(session) { listOf(bridge) }

        assertTrue(response.hasRemoteAttachment)
        assertEquals(bridge.deviceId.toString(), response.attachedDeviceId)
    }

    @Test
    fun `map sets hasRemoteAttachment false when no bridge matches session`() {
        val session = makeSession()
        val otherBridge = buildFakeBridge(sessionId = UUID.randomUUID())

        val response = SessionResponseMapper.map(session) { listOf(otherBridge) }

        assertFalse(response.hasRemoteAttachment)
        assertNull(response.attachedDeviceId)
    }

    // ── 2. Null / empty field handling ────────────────────────────────────────

    @Test
    fun `attachedDeviceId is null when activeBridges is empty`() {
        val session = makeSession()
        val response = SessionResponseMapper.map(session) { emptyList() }
        assertNull(response.attachedDeviceId)
    }

    @Test
    fun `map handles empty title without throwing`() {
        val session = makeSession(title = "")
        val response = SessionResponseMapper.map(session) { emptyList() }
        assertEquals("", response.title)
        assertNotNull(response.id)
    }

    // ── 3. All TerminalSessionState values ────────────────────────────────────

    @Test
    fun `Running state maps to running`() {
        val response = SessionResponseMapper.map(makeSession(TerminalSessionState.Running)) { emptyList() }
        assertEquals("running", response.state)
    }

    @Test
    fun `Exited state maps to exited`() {
        val response = SessionResponseMapper.map(makeSession(TerminalSessionState.Exited(0))) { emptyList() }
        assertEquals("exited", response.state)
    }

    @Test
    fun `Exited state with non-zero code maps to exited`() {
        val response = SessionResponseMapper.map(makeSession(TerminalSessionState.Exited(137))) { emptyList() }
        assertEquals("exited", response.state)
    }

    @Test
    fun `HasActivity state maps to unknown`() {
        val response = SessionResponseMapper.map(makeSession(TerminalSessionState.HasActivity)) { emptyList() }
        assertEquals("unknown", response.state)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Constructs a minimal [RemoteSessionBridge] stub using mockk.
     * Only [deviceId] and [sessionId] matter for the mapper — all other
     * members are irrelevant and left relaxed.
     */
    private fun buildFakeBridge(
        sessionId: UUID = UUID.randomUUID(),
        deviceId: UUID = UUID.randomUUID(),
    ): RemoteSessionBridge {
        // RemoteSessionBridge requires a real constructor with Ktor/coroutine deps.
        // Use io.mockk.mockk to avoid standing up real WS sessions.
        val bridge = io.mockk.mockk<RemoteSessionBridge>(relaxed = true)
        io.mockk.every { bridge.sessionId } returns sessionId
        io.mockk.every { bridge.deviceId } returns deviceId
        return bridge
    }
}

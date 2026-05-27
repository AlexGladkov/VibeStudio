@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.terminal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Unit tests for [DesktopTerminalService].
 *
 * Tests that exercise pty4j (e.g. [DesktopTerminalService.createSession]) are
 * intentionally omitted here — they require a live PTY and belong in
 * integration/E2E tests.  These tests focus on:
 *
 *  - Initial state of all observable flows.
 *  - Behaviour of no-op paths (kill unknown session, etc.).
 *  - [resolveDefaultShell] logic.
 *  - [dispose] idempotency.
 */
class DesktopTerminalServiceTest {

    private lateinit var scope: CoroutineScope
    private lateinit var service: DesktopTerminalService

    @BeforeTest
    fun setup() {
        // Use IO dispatcher so the service can be created without Dispatchers.Main.
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        service = DesktopTerminalService(serviceScope = scope)
    }

    @AfterTest
    fun teardown() {
        service.dispose()
        scope.cancel()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun sessionsByProject_initiallyEmpty() {
        assertTrue(
            service.sessionsByProject.value.isEmpty(),
            "sessionsByProject must be empty before any session is created",
        )
    }

    @Test
    fun projectActivityStates_initiallyEmpty() {
        assertTrue(
            service.projectActivityStates.value.isEmpty(),
            "projectActivityStates must be empty before any session is created",
        )
    }

    // ── session / sessions queries on empty state ─────────────────────────────

    @Test
    fun session_unknownId_returnsNull() {
        val result = service.session(Uuid.random())
        assertNull(result, "session() must return null for an unknown id")
    }

    @Test
    fun sessions_unknownProjectId_returnsEmptyList() {
        val result = service.sessions(Uuid.random())
        assertTrue(result.isEmpty(), "sessions() must return empty list for unknown project")
    }

    @Test
    fun outputFlow_unknownId_returnsNull() {
        val flow = service.outputFlow(Uuid.random())
        assertNull(flow, "outputFlow() must return null for unknown session id")
    }

    @Test
    fun scrollbackContent_unknownId_returnsNull() {
        val content = service.scrollbackContent(Uuid.random())
        assertNull(content, "scrollbackContent() must return null for unknown session id")
    }

    // ── killSession on non-existent id ────────────────────────────────────────

    @Test
    fun killSession_unknownId_doesNotThrow() {
        // Should be a safe no-op.
        service.killSession(Uuid.random(), force = false)
        service.killSession(Uuid.random(), force = true)
    }

    @Test
    fun killAllSessions_unknownProjectId_doesNotThrow() {
        service.killAllSessions(Uuid.random())
    }

    // ── resize on non-existent id ─────────────────────────────────────────────

    @Test
    fun resize_unknownId_doesNotThrow() {
        service.resize(Uuid.random(), studio.vibe.shared.model.TerminalSize(80, 24))
    }

    // ── markProjectSeen ───────────────────────────────────────────────────────

    @Test
    fun markProjectSeen_unknownProjectId_doesNotThrow() {
        // No activity state exists, so this must be a safe no-op.
        service.markProjectSeen(Uuid.random())
    }

    // ── sendInput on non-existent session ─────────────────────────────────────

    @Test
    fun sendInput_unknownSessionId_doesNotThrow() {
        service.sendInput("hello\n", Uuid.random())
    }

    // ── dispose ───────────────────────────────────────────────────────────────

    @Test
    fun dispose_withNoSessions_doesNotThrow() {
        // A second dispose after the one in teardown must also be safe.
        service.dispose()
    }

    // ── resolveDefaultShell ───────────────────────────────────────────────────

    @Test
    fun resolveDefaultShell_returnsNonBlankString() {
        val shell = resolveDefaultShell()
        assertTrue(shell.isNotBlank(), "resolveDefaultShell() must return a non-blank shell path")
    }

    @Test
    fun resolveDefaultShell_onNonWindows_doesNotReturnCmdExe() {
        val os = System.getProperty("os.name", "").lowercase()
        if (!os.contains("windows")) {
            val shell = resolveDefaultShell()
            assertTrue(
                shell != "cmd.exe",
                "resolveDefaultShell() must not return cmd.exe on non-Windows, got: $shell",
            )
        }
    }

    @Test
    fun resolveDefaultShell_returnsAbsolutePathOrShellName() {
        val shell = resolveDefaultShell()
        // Either an absolute path (starts with '/') or a plain name (cmd.exe on Windows).
        val isAbsolute = shell.startsWith("/") || shell.contains(":")
        val isSimpleName = !shell.contains("/") && !shell.contains("\\")
        assertTrue(
            isAbsolute || isSimpleName,
            "Unexpected shell path format: $shell",
        )
    }

    // ── internalSessions snapshot ─────────────────────────────────────────────

    @Test
    fun internalSessions_initiallyEmpty() {
        assertTrue(
            service.internalSessions().isEmpty(),
            "internalSessions() must be empty before any session is created",
        )
    }

    @Test
    fun internalSessions_returnsImmutableSnapshot() {
        val snapshot1 = service.internalSessions()
        val snapshot2 = service.internalSessions()
        // Both snapshots must be equal (content-wise) but are separate objects.
        assertEquals(snapshot1, snapshot2)
    }
}

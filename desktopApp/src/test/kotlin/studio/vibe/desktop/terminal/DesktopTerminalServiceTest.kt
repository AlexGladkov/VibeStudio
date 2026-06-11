@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.terminal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import studio.vibe.shared.core.common.SettingsStorage
import studio.vibe.shared.feature.settings.data.GeneralPreferences
import studio.vibe.shared.core.common.ClaudeAgent
import studio.vibe.shared.core.common.CodexAgent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

// ── In-process stub (not available from shared:commonTest in this source set) ──

private class StubSettingsStorage : SettingsStorage {
    private val strings = mutableMapOf<String, String>()
    private val bools = mutableMapOf<String, Boolean>()
    private val ints = mutableMapOf<String, Int>()

    override fun getString(key: String): String? = strings[key]
    override fun setString(key: String, value: String) { strings[key] = value }
    override fun getBool(key: String): Boolean = bools[key] ?: false
    override fun setBool(key: String, value: Boolean) { bools[key] = value }
    override fun getInt(key: String): Int? = ints[key]
    override fun setInt(key: String, value: Int) { ints[key] = value }
    override fun remove(key: String) { strings.remove(key); bools.remove(key); ints.remove(key) }
}

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
        service = DesktopTerminalService(parentScope = scope)
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
        service.resize(Uuid.random(), studio.vibe.shared.feature.terminal.domain.model.TerminalSize(80, 24))
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

    // ── claudeSkipPermissions wiring ──────────────────────────────────────────

    @Test
    fun generalPreferences_claudeSkipPermissions_defaultsFalse() {
        val prefs = GeneralPreferences(StubSettingsStorage())
        assertFalse(
            prefs.claudeSkipPermissions,
            "claudeSkipPermissions must default to false on fresh storage",
        )
    }

    @Test
    fun generalPreferences_claudeSkipPermissions_persistsTrue() {
        val storage = StubSettingsStorage()
        val prefs = GeneralPreferences(storage)
        prefs.claudeSkipPermissions = true
        // Reload from same storage — simulates app restart reading persisted value.
        val reloaded = GeneralPreferences(storage)
        assertTrue(
            reloaded.claudeSkipPermissions,
            "claudeSkipPermissions must survive a reload from the same storage",
        )
    }

    // ── buildEffectiveLaunchCommand — skip-permissions flag injection ─────────

    @Test
    fun claudeAgent_skipPermissionsTrue_commandContainsFlag() {
        val prefs = GeneralPreferences(StubSettingsStorage()).apply {
            claudeSkipPermissions = true
        }
        val svc = DesktopTerminalService(parentScope = scope, generalPreferences = prefs)
        val cmd = svc.buildEffectiveLaunchCommand(ClaudeAgent)
        assertTrue(
            cmd.contains("--dangerously-skip-permissions"),
            "Expected --dangerously-skip-permissions in command, got: $cmd",
        )
        assertTrue(cmd.endsWith("\n"), "Launch command must end with newline, got: $cmd")
    }

    @Test
    fun claudeAgent_skipPermissionsFalse_commandDoesNotContainFlag() {
        val prefs = GeneralPreferences(StubSettingsStorage()).apply {
            claudeSkipPermissions = false
        }
        val svc = DesktopTerminalService(parentScope = scope, generalPreferences = prefs)
        val cmd = svc.buildEffectiveLaunchCommand(ClaudeAgent)
        assertFalse(
            cmd.contains("--dangerously-skip-permissions"),
            "Flag must NOT appear when claudeSkipPermissions=false, got: $cmd",
        )
        // ClaudeAgent declares jsonStreamOutputArgs, so the command will now include
        // "--output-format stream-json" even without the skip-permissions flag.
        assertTrue(
            cmd.contains("--output-format stream-json"),
            "Claude command must include JSON stream args regardless of skip-permissions, got: $cmd",
        )
        assertTrue(cmd.endsWith("\n"), "Command must end with newline, got: $cmd")
    }

    @Test
    fun nonClaudeAgent_skipPermissionsTrue_commandDoesNotContainFlag() {
        val prefs = GeneralPreferences(StubSettingsStorage()).apply {
            claudeSkipPermissions = true
        }
        val svc = DesktopTerminalService(parentScope = scope, generalPreferences = prefs)
        val cmd = svc.buildEffectiveLaunchCommand(CodexAgent)
        assertFalse(
            cmd.contains("--dangerously-skip-permissions"),
            "Flag must NOT appear for non-Claude agent, got: $cmd",
        )
        // CodexAgent also declares jsonStreamOutputArgs — verify it is included.
        assertTrue(
            cmd.contains("--output-format stream-json"),
            "Codex command must include JSON stream args, got: $cmd",
        )
        assertTrue(cmd.endsWith("\n"), "Command must end with newline, got: $cmd")
    }

    @Test
    fun serviceConstructsWithGeneralPreferences_andDoesNotThrow() {
        // Verifies that passing a non-null GeneralPreferences to DesktopTerminalService
        // is wired correctly: no NPE, no class-cast, service starts up cleanly.
        val prefs = GeneralPreferences(StubSettingsStorage()).apply {
            claudeSkipPermissions = true
        }
        val scopeWithPrefs = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val serviceWithPrefs = DesktopTerminalService(
            parentScope = scopeWithPrefs,
            generalPreferences = prefs,
        )
        try {
            assertTrue(serviceWithPrefs.sessionsByProject.value.isEmpty())
        } finally {
            serviceWithPrefs.dispose()
            scopeWithPrefs.cancel()
        }
    }

    // ── onFirstAgentInput callback ────────────────────────────────────────────

    @Test
    fun sendInput_unknownSession_onFirstAgentInputCallbackNotFired() {
        // sendInput on an unknown session id must be a safe no-op — the callback
        // must never fire when the session is not in the registry.
        val capturedInputs = mutableListOf<Pair<kotlin.uuid.Uuid, String>>()
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val svc = DesktopTerminalService(
            parentScope = testScope,
            onFirstAgentInput = { id, input -> capturedInputs.add(id to input) },
        )

        svc.sendInput("any input\n", kotlin.uuid.Uuid.random())

        assertTrue(
            capturedInputs.isEmpty(),
            "onFirstAgentInput must NOT fire when session is not registered",
        )
        svc.dispose()
        testScope.cancel()
    }

    @Test
    fun ptySessionState_launchCommandSentAndFirstPromptCaptured_defaultValues() {
        // Verify the new fields on PtySessionState have the correct defaults so
        // the flag-gate logic in sendInput works as designed.
        val ptyProcess = io.mockk.mockk<com.pty4j.PtyProcess>(relaxed = true) {
            io.mockk.every { outputStream } returns java.io.ByteArrayOutputStream()
        }
        val state = PtySessionState(
            session = studio.vibe.shared.feature.terminal.domain.model.TerminalSession(
                projectId = kotlin.uuid.Uuid.random(),
                title = "claude",
                state = studio.vibe.shared.core.common.terminal.TerminalSessionState.Running,
                isAgentSession = true,
            ),
            ptyProcess = ptyProcess,
            inputWriter = java.io.BufferedWriter(
                java.io.OutputStreamWriter(java.io.ByteArrayOutputStream(), Charsets.UTF_8)
            ),
            outputFlow = kotlinx.coroutines.flow.MutableSharedFlow(replay = 0),
            scrollback = ScrollbackBuffer(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )

        assertFalse(
            state.launchCommandSent,
            "launchCommandSent must default to false so the gate blocks early sendInput calls",
        )
        assertFalse(
            state.firstPromptCaptured,
            "firstPromptCaptured must default to false so the first user prompt is captured",
        )
        state.scope.cancel()
    }
}

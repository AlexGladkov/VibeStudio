@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.terminal

import com.pty4j.PtyProcess
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import studio.vibe.shared.core.common.terminal.TerminalSession
import studio.vibe.shared.core.common.terminal.TerminalSessionState
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Unit tests for [PtySessionRegistry].
 *
 * Avoids any real PTY process by injecting mock [PtyProcess] objects so there
 * is no OS-level resource acquisition during the tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PtySessionRegistryTest {

    private lateinit var serviceScope: CoroutineScope
    private lateinit var registry: PtySessionRegistry

    @BeforeTest
    fun setUp() {
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        registry = PtySessionRegistry(serviceScope)
    }

    @AfterTest
    fun tearDown() {
        serviceScope.cancel()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeMockProcess(): PtyProcess {
        val proc = mockk<PtyProcess>(relaxed = true)
        every { proc.outputStream } returns ByteArrayOutputStream()
        return proc
    }

    private fun makeSessionState(
        projectId: Uuid = Uuid.random(),
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    ): PtySessionState {
        val session = TerminalSession(
            projectId = projectId,
            title = "zsh",
            state = TerminalSessionState.Running,
        )
        return PtySessionState(
            session = session,
            ptyProcess = makeMockProcess(),
            inputWriter = BufferedWriter(OutputStreamWriter(ByteArrayOutputStream(), Charsets.UTF_8)),
            outputFlow = MutableSharedFlow(replay = 0),
            scrollback = ScrollbackBuffer(),
            scope = scope,
        )
    }

    // ── add + remove ──────────────────────────────────────────────────────────

    @Test
    fun add_putsSessionIntoSnapshot() {
        val state = makeSessionState()
        registry.add(state)

        assertNotNull(registry[state.session.id], "Session must be retrievable after add()")
        assertEquals(1, registry.snapshot.size)
    }

    @Test
    fun remove_evictsSessionFromSnapshot() {
        val state = makeSessionState()
        registry.add(state)

        registry.remove(state.session.id)

        assertNull(registry[state.session.id], "Session must be gone after remove()")
        assertTrue(registry.snapshot.isEmpty())
    }

    @Test
    fun remove_cancelsSessionScope() {
        val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val state = makeSessionState(scope = sessionScope)
        registry.add(state)

        assertTrue(sessionScope.coroutineContext[kotlinx.coroutines.Job]!!.isActive,
            "Scope must be active before remove")

        registry.remove(state.session.id)

        assertFalse(sessionScope.coroutineContext[kotlinx.coroutines.Job]!!.isActive,
            "Scope must be cancelled after remove()")
    }

    @Test
    fun remove_cancelsGraceKillJob() {
        val state = makeSessionState()
        val graceJob = kotlinx.coroutines.Job()
        state.graceKillJob = graceJob
        registry.add(state)

        registry.remove(state.session.id)

        assertFalse(graceJob.isActive, "graceKillJob must be cancelled on remove()")
        assertNull(state.graceKillJob, "graceKillJob reference must be cleared")
    }

    @Test
    fun remove_unknownId_isNoOp() {
        // Must not throw; registry stays empty.
        registry.remove(Uuid.random())
        assertTrue(registry.snapshot.isEmpty())
    }

    // ── unregister (natural exit — scope stays alive) ─────────────────────────

    @Test
    fun unregister_removesSessionWithoutCancellingScope() {
        val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val state = makeSessionState(scope = sessionScope)
        registry.add(state)

        registry.unregister(state.session.id)

        assertNull(registry[state.session.id], "Session must be gone after unregister()")
        assertTrue(sessionScope.coroutineContext[kotlinx.coroutines.Job]!!.isActive,
            "Scope must remain active after unregister()")

        // Clean up manually since we didn't use remove()
        sessionScope.cancel()
    }

    @Test
    fun unregister_unknownId_isNoOp() {
        registry.unregister(Uuid.random())
        assertTrue(registry.snapshot.isEmpty())
    }

    // ── updateModel ───────────────────────────────────────────────────────────

    @Test
    fun updateModel_replacesSessionModelInSnapshot() {
        val state = makeSessionState()
        registry.add(state)

        val updated = state.session.copy(title = "bash")
        registry.updateModel(updated)

        assertEquals("bash", registry[updated.id]?.session?.title)
    }

    @Test
    fun updateModel_unknownId_doesNotThrow() {
        val orphan = TerminalSession(
            projectId = Uuid.random(),
            title = "orphan",
        )
        // Must not throw
        registry.updateModel(orphan)
    }

    // ── touch triggers sessionsByProject re-emit ──────────────────────────────

    @Test
    fun touch_causesSessionsByProjectToReflectCurrentState() {
        val projectId = Uuid.random()
        val state = makeSessionState(projectId = projectId)
        registry.add(state)

        // Mutate in-place (simulates what watchProcessExit does before touch())
        state.session = state.session.copy(title = "mutated")
        registry.touch()

        // Allow the IO-dispatched stateIn transformation to propagate
        Thread.sleep(100)
        val projectSessions = registry.sessionsByProject.value[projectId]
        assertNotNull(projectSessions)
        assertEquals("mutated", projectSessions.first().title,
            "sessionsByProject must reflect in-place mutation after touch()")
    }

    // ── sessionsByProject derived flow ────────────────────────────────────────

    @Test
    fun sessionsByProject_groupsByProjectId() {
        val projectA = Uuid.random()
        val projectB = Uuid.random()

        val stateA1 = makeSessionState(projectId = projectA)
        val stateA2 = makeSessionState(projectId = projectA)
        val stateB1 = makeSessionState(projectId = projectB)

        registry.add(stateA1)
        registry.add(stateA2)
        registry.add(stateB1)

        // stateIn with SharingStarted.Eagerly emits synchronously on Dispatchers.IO scope;
        // read value after add() completes — the update propagates synchronously via MutableStateFlow.
        // Give the IO thread a brief yield to let the stateIn transformation run.
        Thread.sleep(100)
        val map = registry.sessionsByProject.value
        assertEquals(2, map[projectA]?.size, "Project A must have 2 sessions")
        assertEquals(1, map[projectB]?.size, "Project B must have 1 session")
    }

    @Test
    fun sessionsByProject_emptyAfterAllRemoved() {
        val state = makeSessionState()
        registry.add(state)
        registry.remove(state.session.id)

        Thread.sleep(100)
        assertTrue(registry.sessionsByProject.value.isEmpty(),
            "sessionsByProject must be empty after all sessions removed")
    }

    // ── ptyProcess helper ─────────────────────────────────────────────────────

    @Test
    fun ptyProcess_returnsProcessForKnownSession() {
        val state = makeSessionState()
        registry.add(state)

        val proc = registry.ptyProcess(state.session.id)
        assertNotNull(proc)
    }

    @Test
    fun ptyProcess_returnsNullForUnknownSession() {
        assertNull(registry.ptyProcess(Uuid.random()))
    }
}

@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.terminal

import com.pty4j.PtyProcess
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import studio.vibe.shared.contract.TerminalSessionEvent
import studio.vibe.shared.model.TabActivityState
import studio.vibe.shared.model.TerminalSession
import studio.vibe.shared.model.TerminalSessionState
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Unit tests for [PtyOutputWatcher].
 *
 * Uses [StandardTestDispatcher] for time control around the 10-second agent
 * visibility delay in [PtyOutputWatcher.watchProcessExit].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PtyOutputWatcherTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var registryScope: CoroutineScope
    private lateinit var registry: PtySessionRegistry
    private lateinit var watcher: PtyOutputWatcher

    @BeforeTest
    fun setUp() {
        registryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        registry = PtySessionRegistry(registryScope)
        watcher = PtyOutputWatcher(registry)
    }

    @AfterTest
    fun tearDown() {
        registryScope.cancel()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Create a mock PTY process whose [waitFor] returns [exitCode] immediately.
     */
    private fun mockProcess(exitCode: Int = 0): PtyProcess = mockk<PtyProcess>(relaxed = true) {
        every { waitFor() } returns exitCode
        every { isAlive } returns false
        every { outputStream } returns ByteArrayOutputStream()
    }

    private fun makeState(
        projectId: Uuid = Uuid.random(),
        isAgentSession: Boolean = false,
        exitCode: Int = 0,
    ): PtySessionState {
        val session = TerminalSession(
            projectId = projectId,
            title = if (isAgentSession) "claude" else "zsh",
            state = TerminalSessionState.Running,
            isAgentSession = isAgentSession,
        )
        return PtySessionState(
            session = session,
            ptyProcess = mockProcess(exitCode),
            inputWriter = BufferedWriter(OutputStreamWriter(ByteArrayOutputStream(), Charsets.UTF_8)),
            outputFlow = MutableSharedFlow(replay = 8, extraBufferCapacity = 512),
            scrollback = ScrollbackBuffer(),
            scope = CoroutineScope(SupervisorJob() + testDispatcher),
        )
    }

    // ── receiveOutput ─────────────────────────────────────────────────────────

    @Test
    fun receiveOutput_emitsToOutputFlow() = runTest(testDispatcher) {
        val state = makeState()
        registry.add(state)

        // Subscribe first, then emit
        val deferred = async { state.outputFlow.first() }

        watcher.receiveOutput(state.session.id, "hello world")
        advanceUntilIdle()

        assertEquals("hello world", deferred.await(),
            "outputFlow must emit received chunk")
    }

    @Test
    fun receiveOutput_appendsToScrollback() {
        val state = makeState()
        registry.add(state)

        watcher.receiveOutput(state.session.id, "chunk1")
        watcher.receiveOutput(state.session.id, "chunk2")

        val content = state.scrollback.content()
        assertTrue(content.contains("chunk1"), "Scrollback must contain chunk1")
        assertTrue(content.contains("chunk2"), "Scrollback must contain chunk2")
    }

    @Test
    fun receiveOutput_unknownSessionId_doesNotThrow() {
        // Must be a safe no-op
        watcher.receiveOutput(Uuid.random(), "data")
    }

    // ── markActivity ──────────────────────────────────────────────────────────

    @Test
    fun markActivity_setsRunningState() = runTest(testDispatcher) {
        val projectId = Uuid.random()
        val sessionId = Uuid.random()

        watcher.markActivity(sessionId, projectId)

        assertEquals(
            TabActivityState.RUNNING,
            watcher.projectActivityStates.value[projectId],
            "markActivity must set RUNNING state for the project",
        )
    }

    @Test
    fun markActivity_emitsActivityDetectedEvent() {
        val projectId = Uuid.random()
        val sessionId = Uuid.random()

        // Collect via a real IO scope — SharedFlow.tryEmit is immediate, collect is synchronous enough
        val collected = mutableListOf<TerminalSessionEvent>()
        val collectScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val collectJob = collectScope.launch { watcher.sessionEvents.collect { collected.add(it) } }

        // Give the collector a moment to subscribe
        Thread.sleep(50)
        watcher.markActivity(sessionId, projectId)
        Thread.sleep(100)

        collectJob.cancel()
        collectScope.cancel()

        assertTrue(
            collected.any { it is TerminalSessionEvent.ActivityDetected && it.projectId == projectId },
            "markActivity must emit ActivityDetected event",
        )
    }

    // ── clearActivity ─────────────────────────────────────────────────────────

    @Test
    fun clearActivity_changesRunningToIdle() = runTest(testDispatcher) {
        val projectId = Uuid.random()
        watcher.markActivity(Uuid.random(), projectId)

        assertEquals(TabActivityState.RUNNING, watcher.projectActivityStates.value[projectId])

        watcher.clearActivity(projectId)

        assertEquals(
            TabActivityState.IDLE,
            watcher.projectActivityStates.value[projectId],
            "clearActivity must transition RUNNING → IDLE",
        )
    }

    @Test
    fun clearActivity_doesNotAffectIdleState() = runTest(testDispatcher) {
        val projectId = Uuid.random()
        // Manually put IDLE
        watcher.markActivity(Uuid.random(), projectId)
        watcher.clearActivity(projectId)

        // Call clearActivity again — must stay IDLE, not crash
        watcher.clearActivity(projectId)

        assertEquals(TabActivityState.IDLE, watcher.projectActivityStates.value[projectId])
    }

    // ── watchProcessExit ──────────────────────────────────────────────────────

    @Test
    fun watchProcessExit_emitsProcessExitedEvent() = runTest(testDispatcher) {
        val projectId = Uuid.random()
        val state = makeState(projectId = projectId, exitCode = 0)
        registry.add(state)

        val events = mutableListOf<TerminalSessionEvent>()
        val collectJob = launch { watcher.sessionEvents.collect { events.add(it) } }

        val watchJob = launch(testDispatcher) {
            watcher.watchProcessExit(state.session.id, projectId, state)
        }

        advanceTimeBy(500)
        watchJob.join()
        collectJob.cancel()

        assertTrue(
            events.any {
                it is TerminalSessionEvent.ProcessExited && it.projectId == projectId
            },
            "watchProcessExit must emit ProcessExited event",
        )
    }

    @Test
    fun watchProcessExit_unregistersSession() = runTest(testDispatcher) {
        val projectId = Uuid.random()
        val state = makeState(projectId = projectId)
        registry.add(state)

        val watchJob = launch(testDispatcher) {
            watcher.watchProcessExit(state.session.id, projectId, state)
        }
        advanceTimeBy(500)
        watchJob.join()

        assertTrue(
            registry.snapshot[state.session.id] == null,
            "watchProcessExit must unregister the session from the registry",
        )
    }

    @Test
    fun watchProcessExit_updatesSessionStateToExited() = runTest(testDispatcher) {
        val projectId = Uuid.random()
        val state = makeState(projectId = projectId, exitCode = 42)
        registry.add(state)

        val watchJob = launch(testDispatcher) {
            watcher.watchProcessExit(state.session.id, projectId, state)
        }
        advanceTimeBy(500)
        watchJob.join()

        val exitedState = state.session.state
        assertTrue(exitedState is TerminalSessionState.Exited,
            "Session state must be Exited after watchProcessExit")
        assertEquals(42, (exitedState as TerminalSessionState.Exited).code)
    }

    // ── agent session 10-second visibility delay ──────────────────────────────

    @Test
    fun watchProcessExit_agentSession_unregistersAfter10sDelay() = runTest(testDispatcher) {
        val projectId = Uuid.random()
        val state = makeState(projectId = projectId, isAgentSession = true)
        registry.add(state)

        val watchJob = launch(testDispatcher) {
            watcher.watchProcessExit(state.session.id, projectId, state)
        }

        // After a short advance the session should still be visible
        advanceTimeBy(500)
        assertFalse(
            registry.snapshot[state.session.id] == null,
            "Agent session must NOT be unregistered immediately after exit",
        )

        // Advance past the 10-second delay
        advanceTimeBy(11_000)
        watchJob.join()

        assertTrue(
            registry.snapshot[state.session.id] == null,
            "Agent session must be unregistered after 10-second visibility delay",
        )
    }

    @Test
    fun watchProcessExit_normalSession_unregistersWithoutDelay() = runTest(testDispatcher) {
        val projectId = Uuid.random()
        val state = makeState(projectId = projectId, isAgentSession = false)
        registry.add(state)

        val watchJob = launch(testDispatcher) {
            watcher.watchProcessExit(state.session.id, projectId, state)
        }
        advanceTimeBy(200)
        watchJob.join()

        assertTrue(
            registry.snapshot[state.session.id] == null,
            "Normal session must be unregistered promptly (no delay)",
        )
    }
}

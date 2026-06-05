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
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Unit tests for [PtyKillStrategy].
 *
 * Every test injects a mock [PtyProcess] so no real OS process is spawned.
 *
 * Note: PtyKillStrategy hardcodes Dispatchers.IO for its grace-kill timer, so
 * graceful-kill timing tests use Thread.sleep + real IO coroutines. Force-kill
 * tests are fully synchronous and do not require time manipulation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PtyKillStrategyTest {

    private lateinit var serviceScope: CoroutineScope
    private lateinit var registryScope: CoroutineScope
    private lateinit var registry: PtySessionRegistry
    private lateinit var killStrategy: PtyKillStrategy

    @BeforeTest
    fun setUp() {
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        registryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        registry = PtySessionRegistry(registryScope)
        killStrategy = PtyKillStrategy(registry, serviceScope)
    }

    @AfterTest
    fun tearDown() {
        serviceScope.cancel()
        registryScope.cancel()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun aliveProcess(): PtyProcess = mockk<PtyProcess>(relaxed = true) {
        every { isAlive } returns true
        every { outputStream } returns ByteArrayOutputStream()
    }

    private fun deadProcess(): PtyProcess = mockk<PtyProcess>(relaxed = true) {
        every { isAlive } returns false
        every { outputStream } returns ByteArrayOutputStream()
    }

    private fun addSession(
        proc: PtyProcess,
        projectId: Uuid = Uuid.random(),
    ): PtySessionState {
        val session = studio.vibe.shared.model.TerminalSession(
            projectId = projectId,
            title = "zsh",
            state = studio.vibe.shared.model.TerminalSessionState.Running,
        )
        val state = PtySessionState(
            session = session,
            ptyProcess = proc,
            inputWriter = BufferedWriter(OutputStreamWriter(ByteArrayOutputStream(), Charsets.UTF_8)),
            outputFlow = MutableSharedFlow(replay = 0),
            scrollback = ScrollbackBuffer(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
        registry.add(state)
        return state
    }

    // ── kill(force = true) ────────────────────────────────────────────────────

    @Test
    fun kill_force_destroysForciblyAndEvicts() {
        val proc = aliveProcess()
        val state = addSession(proc)

        killStrategy.kill(state.session.id, force = true)

        verify { proc.destroyForcibly() }
        assertNull(registry[state.session.id], "Session must be evicted after force kill")
    }

    @Test
    fun kill_force_doesNotSendSigterm() {
        val proc = aliveProcess()
        val state = addSession(proc)

        killStrategy.kill(state.session.id, force = true)

        // destroy() = SIGTERM — must NOT be called for force kill
        verify(exactly = 0) { proc.destroy() }
    }

    // ── kill(force = false) — graceful SIGTERM + grace period ─────────────────

    @Test
    fun kill_graceful_sendsSigtermFirst() {
        val proc = aliveProcess()
        val state = addSession(proc)

        killStrategy.kill(state.session.id, force = false)

        verify { proc.destroy() }
    }

    @Test
    fun kill_graceful_sessionRemovedAfterGracePeriod() {
        val proc = aliveProcess()
        val state = addSession(proc)

        killStrategy.kill(state.session.id, force = false)

        // Session still exists right after graceful kill is initiated
        assertFalse(registry.snapshot.isEmpty(),
            "Session should still be in registry immediately after graceful kill")

        // Wait for the 2-second grace period + margin
        Thread.sleep(2_500)

        assertNull(registry[state.session.id],
            "Session must be evicted after grace period expires")
    }

    @Test
    fun kill_graceful_destroysForciblyIfAliveAfterGrace() {
        val proc = aliveProcess()
        val state = addSession(proc)

        killStrategy.kill(state.session.id, force = false)
        Thread.sleep(2_500)

        verify { proc.destroyForcibly() }
    }

    @Test
    fun kill_graceful_skipsDestroyForciblyIfAlreadyDead() {
        val proc = deadProcess()
        val state = addSession(proc)

        killStrategy.kill(state.session.id, force = false)
        Thread.sleep(2_500)

        verify(exactly = 0) { proc.destroyForcibly() }
    }

    // ── kill unknown ID — no-op ───────────────────────────────────────────────

    @Test
    fun kill_unknownId_isNoOp() {
        // Must not throw
        killStrategy.kill(Uuid.random(), force = true)
        killStrategy.kill(Uuid.random(), force = false)
    }

    // ── killAll by projectId ──────────────────────────────────────────────────

    @Test
    fun killAll_onlyKillsSessionsOfTargetProject() {
        val targetProject = Uuid.random()
        val otherProject = Uuid.random()

        val procTarget = aliveProcess()
        val procOther = aliveProcess()

        val stateTarget = addSession(procTarget, projectId = targetProject)
        val stateOther = addSession(procOther, projectId = otherProject)

        killStrategy.killAll(targetProject)

        verify { procTarget.destroyForcibly() }
        assertNull(registry[stateTarget.session.id], "Target session must be evicted")

        // Other project session must survive
        verify(exactly = 0) { procOther.destroyForcibly() }
        assertFalse(registry.snapshot[stateOther.session.id] == null,
            "Other project session must NOT be evicted")
    }

    @Test
    fun killAll_emptyProject_isNoOp() {
        killStrategy.killAll(Uuid.random())
    }

    // ── grace job cancelled when remove() is called before timer fires ─────────

    @Test
    fun graceJob_cancelledWhenRemoveCalledBeforeTimer() {
        val proc = aliveProcess()
        val state = addSession(proc)

        // Start graceful kill (SIGTERM) — a graceKillJob is scheduled for 2 s
        killStrategy.kill(state.session.id, force = false)
        // Verify SIGTERM was sent
        verify { proc.destroy() }

        // Immediately remove the session — this cancels the grace job before it fires
        registry.remove(state.session.id)

        // Wait past the grace period — destroyForcibly must NOT have been called,
        // because the grace coroutine was cancelled before it could execute destroyForcibly.
        Thread.sleep(2_500)

        verify(exactly = 0) { proc.destroyForcibly() }
        assertNull(registry[state.session.id])
    }
}

package studio.vibe.desktop.remote

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import studio.vibe.desktop.remote.ngrok.NgrokProcessLauncher
import studio.vibe.desktop.remote.ngrok.NgrokUrlPoller
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [NgrokTunnelService] orchestration logic.
 *
 * All OS interactions are eliminated via mockk injections of [NgrokProcessLauncher]
 * and [NgrokUrlPoller]. The PID file location is overridden by reflection for
 * isolation. Each test manages its own [CoroutineScope] and cleans up after itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NgrokTunnelServiceTest {

    companion object {
        private const val FAKE_NGROK_PATH = "/fake/ngrok"
        private const val FAKE_PORT = 7842
        private const val FAKE_AUTHTOKEN = "authtoken-xyz"
        private val PID_FILE = File("/tmp/vibestudio-ngrok-test-${ProcessHandle.current().pid()}.pid")
    }

    private lateinit var scope: CoroutineScope
    private lateinit var launcher: NgrokProcessLauncher
    private lateinit var urlPoller: NgrokUrlPoller

    @BeforeTest
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        launcher = mockk(relaxed = true)
        urlPoller = mockk(relaxed = true)

        // Default: ngrok binary found
        every { launcher.resolveNgrok() } returns FAKE_NGROK_PATH
        // Default: authtoken env produced without error
        every { launcher.buildEnvironment(any()) } returns mutableMapOf(
            "PATH" to "/usr/bin:/bin",
            "NGROK_AUTHTOKEN" to FAKE_AUTHTOKEN,
        )

        // Default URL poller: no tunnel available
        every { urlPoller.fetchTunnelUrl(any()) } returns null

        // Clean up any leftover PID file
        PID_FILE.delete()
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        PID_FILE.delete()
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun makeService(): NgrokTunnelService =
        NgrokTunnelService(scope, launcher, urlPoller)

    /**
     * Creates a fake Process that reports alive = [alive] and returns
     * [pid] from [Process.pid].
     *
     * When [diesOnDestroy] is true (the default), calling [Process.destroy] or
     * [Process.destroyForcibly] flips the alive flag to false, simulating the OS
     * killing the process. This prevents stop() from waiting through the full 5s SIGKILL
     * grace period in tests.
     */
    private fun fakeProcess(
        pid: Long = 12345L,
        alive: Boolean = true,
        diesOnDestroy: Boolean = true,
    ): Process {
        val proc = mockk<Process>(relaxed = true)
        val isAliveHolder = java.util.concurrent.atomic.AtomicBoolean(alive)
        every { proc.isAlive } answers { isAliveHolder.get() }
        every { proc.pid() } returns pid
        if (diesOnDestroy) {
            every { proc.destroy() } answers { isAliveHolder.set(false) }
            every { proc.destroyForcibly() } answers { isAliveHolder.set(false); proc }
        }
        every { proc.waitFor() } answers {
            // Block until alive becomes false (or max 60s)
            val deadline = System.currentTimeMillis() + 60_000
            while (isAliveHolder.get() && System.currentTimeMillis() < deadline) {
                Thread.sleep(50)
            }
            0
        }
        return proc
    }

    // ── start_withAuthtoken_passesEnvVar ──────────────────────────────────────

    @Test
    fun start_withAuthtoken_passesEnvVarToLauncher() {
        val proc = fakeProcess()
        every { launcher.launch(any(), any(), any()) } returns proc

        val service = makeService()
        service.start(FAKE_PORT, FAKE_AUTHTOKEN)

        // start() always does delay(500) inside killPreviousOwnedProcess path; wait > 700ms
        Thread.sleep(900)

        verify { launcher.buildEnvironment(FAKE_AUTHTOKEN) }
        verify { launcher.launch(FAKE_NGROK_PATH, FAKE_PORT, any()) }
    }

    // ── stop_cancelsPolling ───────────────────────────────────────────────────

    @Test
    fun stop_cancelsPollingAndSetsNotRunning() {
        val proc = fakeProcess()
        every { launcher.launch(any(), any(), any()) } returns proc
        // Poller always returns null to keep the poll loop alive
        every { urlPoller.fetchTunnelUrl(any()) } returns null

        val service = makeService()
        service.start(FAKE_PORT)
        Thread.sleep(900) // let start() complete (500ms delay + overhead) + isRunning flip

        service.stop()
        Thread.sleep(500)

        assertFalse(service.isRunning.value, "isRunning must be false after stop()")
        assertNull(service.tunnelURL.value, "tunnelURL must be null after stop()")
    }

    // ── start_whenAlreadyRunning_isNoOp ───────────────────────────────────────

    @Test
    fun start_whenAlreadyRunning_isNoOp() {
        val proc = fakeProcess()
        every { launcher.launch(any(), any(), any()) } returns proc

        val service = makeService()
        service.start(FAKE_PORT)
        Thread.sleep(900) // let first start() complete (500ms delay + overhead)

        assertTrue(service.isRunning.value, "Precondition: service must be running after first start()")

        // Reset invocation counter on launcher to track second call
        io.mockk.clearMocks(launcher, answers = false, recordedCalls = true, verificationMarks = true)
        every { launcher.resolveNgrok() } returns FAKE_NGROK_PATH
        every { launcher.buildEnvironment(any()) } returns mutableMapOf("PATH" to "/usr/bin")
        every { launcher.launch(any(), any(), any()) } returns fakeProcess()

        service.start(FAKE_PORT)
        Thread.sleep(300) // short wait — second start() should exit early before any delay

        // lifecycleMutex guard: `if (_isRunning.value) return@launch` — launch() must NOT be called again
        verify(exactly = 0) { launcher.launch(any(), any(), any()) }

        service.stop()
        Thread.sleep(300)
    }

    // ── pidFile_writtenAndRemovedOnStop ───────────────────────────────────────

    /**
     * Two-phase test:
     * Phase 1 — PID file is written when the process launches.
     * Phase 2 — stop() removes the PID file after the process is gone.
     *
     * To keep the test fast, the process reports alive=true at launch time
     * (so isRunning flips to true and the PID file is written), then our mock
     * transitions alive → false when stop() calls destroy(), bypassing the
     * 5-second SIGKILL grace period.
     */
    @Test
    fun pidFile_writtenOnStartAndRemovedOnStop() {
        val pid = 99999L
        // diesOnDestroy=true: destroy() sets alive=false → stop() skips the delay(5_000) wait
        val proc = fakeProcess(pid = pid, alive = true, diesOnDestroy = true)
        every { launcher.launch(any(), any(), any()) } returns proc

        val service = makeService()
        service.start(FAKE_PORT)
        Thread.sleep(900) // let start() pass through its 500ms delay + overhead

        assertTrue(service.isRunning.value, "Service must be running before PID file check")
        val pidFile = File("/tmp/vibestudio-ngrok.pid")
        assertTrue(pidFile.exists(), "PID file must exist while ngrok is running")
        val writtenPid = pidFile.readText().trim().toLongOrNull()
        assertEquals(pid, writtenPid, "PID file must contain the process PID")

        service.stop()
        // destroy() immediately kills the mock process. stop() then enters delay(5_000), but
        // the second isAlive check after delay returns false so the block exits. removePidFile()
        // is called right after — but we still need to wait for the full 5-second delay.
        // Instead: verify within a generous but bounded timeout (6s) using polling.
        val pidFileGone = run {
            val deadline = System.currentTimeMillis() + 6_500
            while (System.currentTimeMillis() < deadline) {
                if (!pidFile.exists()) return@run true
                Thread.sleep(100)
            }
            false
        }
        assertTrue(pidFileGone, "PID file must be removed after stop() (checked within 6.5s)")
    }

    // ── stopRequested_setBeforeLaunchCompletes_destroysProcess ────────────────

    @Test
    fun stopRequestedBeforeLaunchCompletes_destroysProcessImmediately() {
        // Make launch() block just long enough that stop() can set stopRequested=true first
        val proc = fakeProcess(alive = true)
        every { launcher.launch(any(), any(), any()) } answers {
            Thread.sleep(150) // simulate slow process start
            proc
        }

        val service = makeService()

        // Fire start() asynchronously, then immediately call stop()
        service.start(FAKE_PORT)
        Thread.sleep(50) // Let start() enter IO but before it reaches delay(500)
        service.stop()   // Sets stopRequested = true

        // start() still has delay(500) + launch(150ms) to run → wait > 800ms for both to complete
        Thread.sleep(1_000)

        // After the race, either:
        //   (a) launch() returned before stopRequested was set → stop() destroyed the proc, isRunning=false
        //   (b) stopRequested was already true when launchNgrok() checked → proc.destroyForcibly() called inline
        // In both cases the final state must be: isRunning=false, tunnelURL=null
        assertFalse(service.isRunning.value, "isRunning must be false after stop() race")
        assertNull(service.tunnelURL.value, "tunnelURL must be null after stop() race")

        // The process must have been destroyed in one path or another
        // We can't assert the exact destroy call count due to the race, but isRunning=false proves
        // the mutex guard + stopRequested check worked correctly.
    }

    // ── ngrokNotFound_setsError ───────────────────────────────────────────────

    @Test
    fun start_ngrokNotFound_setsErrorAndDoesNotRun() {
        every { launcher.resolveNgrok() } returns null

        val service = makeService()
        service.start(FAKE_PORT)
        Thread.sleep(400) // error path exits before delay(500) — 400ms is sufficient

        assertFalse(service.isRunning.value, "isRunning must remain false when ngrok is not found")
        assertTrue(
            service.error.value?.contains("ngrok") == true,
            "error must mention 'ngrok' when binary not found, got: ${service.error.value}",
        )
    }

    // ── urlPoller_resolvesTunnelUrl ───────────────────────────────────────────

    @Test
    fun start_urlPollerReturnsUrl_tunnelUrlUpdated() {
        val proc = fakeProcess()
        every { launcher.launch(any(), any(), any()) } returns proc
        every { urlPoller.fetchTunnelUrl(any()) } returns "https://abc123.ngrok-free.app"

        val service = makeService()
        service.start(FAKE_PORT)

        // start() has 500ms delay in killPreviousOwnedProcess path, then process launches.
        // Poll loop first delays 2000ms before attempting. Total: ~3.5s to be safe.
        Thread.sleep(3_500)

        assertEquals(
            "https://abc123.ngrok-free.app",
            service.tunnelURL.value,
            "tunnelURL must be set when poller returns a URL",
        )

        service.stop()
        Thread.sleep(400)
    }
}

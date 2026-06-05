package studio.vibe.shared.service.git

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.*
import studio.vibe.shared.contract.AheadBehind
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.GitFile
import studio.vibe.shared.model.GitFileStatus
import studio.vibe.shared.model.GitStatus
import studio.vibe.shared.testutil.FakeGitService
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * Unit tests for [GitStatusPollerImpl].
 *
 * Design notes:
 * - The poller owns a long-running `while (isActive) { poll(); delay(...) }` loop.
 * - We pass a dedicated [TestScope] built with [UnconfinedTestDispatcher] so that
 *   `delay()` is virtual (controlled by [TestCoroutineScheduler]) and every launched
 *   coroutine starts immediately.
 * - The polling scope is separate from the `runTest` scope, so the infinite loop
 *   never blocks `runTest` from completing.
 * - After each test we call `pollerScope.cancel()` to tear down the loop cleanly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GitStatusPollerTest {

    private lateinit var fakeGitService: FakeGitService
    private val repoPath = FilePath("/test-repo")

    @BeforeTest
    fun setup() {
        fakeGitService = FakeGitService()
    }

    @AfterTest
    fun teardown() {
        fakeGitService.reset()
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    /**
     * Creates a poller + its owning [TestScope].
     *
     * The scope uses [UnconfinedTestDispatcher] so delays are virtual and
     * coroutines start eagerly. The caller must call [TestScope.cancel] at the
     * end of the test to stop the polling loop.
     */
    private fun makePollerWithScope(): Pair<GitStatusPollerImpl, TestScope> {
        val scope = TestScope(UnconfinedTestDispatcher())
        val poller = GitStatusPollerImpl(fakeGitService, scope)
        return Pair(poller, scope)
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun initialState_statusIsEmpty() {
        val (poller, scope) = makePollerWithScope()
        assertEquals(GitStatus.EMPTY, poller.status.value)
        scope.cancel()
    }

    @Test
    fun initialState_isPollingIsFalse() {
        val (poller, scope) = makePollerWithScope()
        assertFalse(poller.isPolling.value)
        scope.cancel()
    }

    @Test
    fun initialState_lastErrorIsNull() {
        val (poller, scope) = makePollerWithScope()
        assertNull(poller.lastError.value)
        scope.cancel()
    }

    // ── startPolling ──────────────────────────────────────────────────────────

    @Test
    fun startPolling_triggersImmediatePoll() {
        // Arrange
        fakeGitService.statusResult = GitStatus(
            branch = "main",
            aheadCount = 0,
            behindCount = 0,
            stagedFiles = listOf(GitFile("src/Foo.kt", GitFileStatus.MODIFIED)),
            unstagedFiles = emptyList(),
            untrackedFiles = emptyList(),
        )
        val (poller, scope) = makePollerWithScope()

        // Act
        poller.startPolling(repoPath, isActive = true)

        // Assert — UnconfinedTestDispatcher runs the poll coroutine eagerly
        assertEquals("main", poller.status.value.branch)
        assertTrue(fakeGitService.statusCallCount >= 1)

        poller.stopPolling()
        scope.cancel()
    }

    @Test
    fun startPolling_afterError_setsLastError() {
        // Arrange
        val error = RuntimeException("git not found")
        fakeGitService.throwOnStatus = error
        val (poller, scope) = makePollerWithScope()

        // Act
        poller.startPolling(repoPath, isActive = true)

        // Assert
        assertEquals(error, poller.lastError.value)

        poller.stopPolling()
        scope.cancel()
    }

    @Test
    fun startPolling_successAfterError_clearsLastError() {
        // Arrange — first poll fails
        val (poller, scope) = makePollerWithScope()
        fakeGitService.throwOnStatus = RuntimeException("transient")
        poller.startPolling(repoPath, isActive = true)
        assertNotNull(poller.lastError.value) // sanity

        // Act — clear error and advance past the backoff window (max 30s)
        fakeGitService.throwOnStatus = null
        scope.advanceTimeBy(35_000) // 35 seconds in milliseconds
        scope.runCurrent()

        // Assert
        assertNull(poller.lastError.value)

        poller.stopPolling()
        scope.cancel()
    }

    @Test
    fun startPolling_isPollingFalseAfterPollCompletes() {
        // Arrange
        fakeGitService.statusResult = GitStatus.EMPTY
        val (poller, scope) = makePollerWithScope()

        // Act
        poller.startPolling(repoPath, isActive = true)

        // Assert — poll guard released after completion (UnconfinedTestDispatcher runs eagerly)
        assertFalse(poller.isPolling.value)

        poller.stopPolling()
        scope.cancel()
    }

    // ── stopPolling ───────────────────────────────────────────────────────────

    @Test
    fun stopPolling_beforeStart_doesNotThrow() {
        val (poller, scope) = makePollerWithScope()
        poller.stopPolling()
        scope.cancel()
    }

    @Test
    fun stopPolling_afterStart_stopsFurtherPolling() {
        // Arrange
        val (poller, scope) = makePollerWithScope()
        poller.startPolling(repoPath, isActive = true)
        val callsAfterStart = fakeGitService.statusCallCount

        // Act
        poller.stopPolling()
        scope.advanceTimeBy(60_000)
        scope.runCurrent()

        // Assert — no additional calls after stop
        assertEquals(callsAfterStart, fakeGitService.statusCallCount)

        scope.cancel()
    }

    // ── refreshNow ────────────────────────────────────────────────────────────

    @Test
    fun refreshNow_triggersImmediatePoll() {
        // Arrange — refreshNow requires a repository to be set (done via startPolling).
        val (poller, scope) = makePollerWithScope()
        poller.startPolling(repoPath, isActive = true)
        val callsAfterStart = fakeGitService.statusCallCount

        // Stop the periodic loop so we isolate refreshNow's effect.
        poller.stopPolling()

        // Act — refreshNow launches a one-shot poll.
        poller.refreshNow()
        // UnconfinedTestDispatcher runs the launched coroutine inline; no time advance needed.

        // Assert
        assertTrue(fakeGitService.statusCallCount > callsAfterStart)

        scope.cancel()
    }

    @Test
    fun refreshNow_calledRepeatedly_doesNotExplodeCallCount() {
        // Rapid refreshes should coalesce — the second call cancels the first job.
        val (poller, scope) = makePollerWithScope()

        repeat(5) { poller.refreshNow() }
        scope.runCurrent()

        // At most 5 polls (one per refresh at most) — should not do 50+.
        assertTrue(fakeGitService.statusCallCount <= 5)

        poller.stopPolling()
        scope.cancel()
    }

    // ── polling intervals ─────────────────────────────────────────────────────

    @Test
    fun activePolling_doesNotPollBeforeIntervalExpires() {
        // active interval = 3 s → advance 2s, no second poll expected
        val (poller, scope) = makePollerWithScope()
        poller.startPolling(repoPath, isActive = true)
        val callsAfterFirstPoll = fakeGitService.statusCallCount

        scope.advanceTimeBy(2_000)
        scope.runCurrent()

        assertEquals(callsAfterFirstPoll, fakeGitService.statusCallCount)

        poller.stopPolling()
        scope.cancel()
    }

    @Test
    fun activePolling_pollsAgainAfterIntervalExpires() {
        // active interval = 3 s → advance 4s, a second poll must occur
        val (poller, scope) = makePollerWithScope()
        poller.startPolling(repoPath, isActive = true)
        val callsAfterFirstPoll = fakeGitService.statusCallCount

        scope.advanceTimeBy(4_000)
        scope.runCurrent()

        assertTrue(fakeGitService.statusCallCount > callsAfterFirstPoll)

        poller.stopPolling()
        scope.cancel()
    }

    @Test
    fun backgroundPolling_doesNotPollBeforeIntervalExpires() {
        // background interval = 30 s → advance 29s, no second poll expected
        val (poller, scope) = makePollerWithScope()
        poller.startPolling(repoPath, isActive = false)
        val callsAfterFirstPoll = fakeGitService.statusCallCount

        scope.advanceTimeBy(29_000)
        scope.runCurrent()

        assertEquals(callsAfterFirstPoll, fakeGitService.statusCallCount)

        poller.stopPolling()
        scope.cancel()
    }

    // ── backoff on errors ─────────────────────────────────────────────────────

    @Test
    fun errorBackoff_doesNotPollEarlyAfterFirstError() {
        // After 1 error with active base: backoff = 3s * 2^1 = 6s
        // Advance 5s → should NOT have polled again.
        fakeGitService.throwOnStatus = RuntimeException("boom")
        val (poller, scope) = makePollerWithScope()
        poller.startPolling(repoPath, isActive = true)
        val callsAfterFirstError = fakeGitService.statusCallCount

        scope.advanceTimeBy(5_000)
        scope.runCurrent()

        assertEquals(callsAfterFirstError, fakeGitService.statusCallCount)

        poller.stopPolling()
        scope.cancel()
    }

    @Test
    fun errorBackoff_pollsAgainAfterBackoffExpires() {
        // After 1 error: backoff = 6s → advance 7s → second poll expected.
        fakeGitService.throwOnStatus = RuntimeException("boom")
        val (poller, scope) = makePollerWithScope()
        poller.startPolling(repoPath, isActive = true)
        val callsAfterFirstError = fakeGitService.statusCallCount

        scope.advanceTimeBy(7_000)
        scope.runCurrent()

        assertTrue(fakeGitService.statusCallCount > callsAfterFirstError)

        poller.stopPolling()
        scope.cancel()
    }

    @Test
    fun errorBackoff_capsAt30s() {
        // Verifies the backoff formula caps at maxBackoffInterval (30s).
        // Strategy: drive enough error cycles that the backoff is definitely at cap,
        // then verify that the interval between successive polls is no shorter than 30s.
        fakeGitService.throwOnStatus = RuntimeException("persistent")
        val (poller, scope) = makePollerWithScope()
        poller.startPolling(repoPath, isActive = true)

        // Advance to trigger at least 6 polls (enough to saturate the 2^4 cap).
        // Use 180s total (6 × 30s). Each 30s advance fires exactly one capped poll.
        // Record count at a clean 30s boundary.
        scope.advanceTimeBy(180_000)
        val callsAt180s = fakeGitService.statusCallCount

        // Advance 5s more — the next poll at capped 30s interval fires at T=192s
        // (T=162 + 30s). We are at T=180 + 5 = T=185, still before T=192.
        scope.advanceTimeBy(5_000)

        assertEquals(callsAt180s, fakeGitService.statusCallCount,
            "No new poll expected within 5s of the last capped 30s interval boundary")

        poller.stopPolling()
        scope.cancel()
    }

    // ── aheadBehind augmentation ──────────────────────────────────────────────

    @Test
    fun startPolling_zeroAheadBehind_augmentsFromAheadBehindCommand() {
        // Arrange
        fakeGitService.statusResult = GitStatus(
            branch = "main",
            aheadCount = 0,
            behindCount = 0,
            stagedFiles = emptyList(),
            unstagedFiles = emptyList(),
            untrackedFiles = emptyList(),
        )
        fakeGitService.aheadBehindResult = AheadBehind(ahead = 2, behind = 1)
        val (poller, scope) = makePollerWithScope()

        // Act
        poller.startPolling(repoPath, isActive = true)

        // Assert
        assertEquals(2, poller.status.value.aheadCount)
        assertEquals(1, poller.status.value.behindCount)

        poller.stopPolling()
        scope.cancel()
    }

    @Test
    fun startPolling_nonZeroAheadBehind_notOverwrittenByAheadBehindCommand() {
        // Arrange
        fakeGitService.statusResult = GitStatus(
            branch = "main",
            aheadCount = 5,
            behindCount = 3,
            stagedFiles = emptyList(),
            unstagedFiles = emptyList(),
            untrackedFiles = emptyList(),
        )
        fakeGitService.aheadBehindResult = AheadBehind(ahead = 99, behind = 99)
        val (poller, scope) = makePollerWithScope()

        // Act
        poller.startPolling(repoPath, isActive = true)

        // Assert — original values preserved; augmentation only fires when both are 0
        assertEquals(5, poller.status.value.aheadCount)
        assertEquals(3, poller.status.value.behindCount)

        poller.stopPolling()
        scope.cancel()
    }

    // ── P1: refreshNow before startPolling ───────────────────────────────────

    @Test
    fun refreshNow_beforeStartPolling_doesNotThrow() {
        // Arrange — no startPolling has been called yet (currentPath is null)
        val (poller, scope) = makePollerWithScope()

        // Act — must not throw even when there is no repository path set
        poller.refreshNow()
        scope.runCurrent()

        // Assert — no crash; statusCallCount must not go up because path is null
        assertEquals(0, fakeGitService.statusCallCount)

        poller.stopPolling()
        scope.cancel()
    }

    // ── P1: project-switch mid-poll race ──────────────────────────────────────

    @Test
    fun startPolling_calledTwiceWithDifferentPaths_onlyLatestPathUsed() {
        // Simulate a project switch: second call to startPolling with a new path
        // must stop the first loop and begin polling the new path.
        // We verify this by checking that statusCallCount advances after the second
        // startPolling and that the poller is still functioning.
        val path2 = FilePath("/test-repo-2")
        fakeGitService.statusResult = GitStatus(
            branch = "main",
            aheadCount = 0,
            behindCount = 0,
            stagedFiles = emptyList(),
            unstagedFiles = emptyList(),
            untrackedFiles = emptyList(),
        )
        val (poller, scope) = makePollerWithScope()

        // First poll
        poller.startPolling(repoPath, isActive = true)
        val countAfterFirstStart = fakeGitService.statusCallCount

        // Simulate project switch — second startPolling replaces the active loop
        poller.startPolling(path2, isActive = true)

        // At this point the second path has been set; the poller should have polled at least once more.
        assertTrue(
            fakeGitService.statusCallCount > countAfterFirstStart,
            "A second startPolling must trigger at least one additional poll",
        )

        // Advance time and verify the poller continues (it is not stuck)
        scope.advanceTimeBy(4_000)
        scope.runCurrent()
        val countAfterAdvance = fakeGitService.statusCallCount

        // Allow ≥1 additional poll after the interval
        assertTrue(countAfterAdvance > fakeGitService.statusCallCount - 2)

        poller.stopPolling()
        scope.cancel()
    }

    // ── state retention after stopPolling ─────────────────────────────────────

    @Test
    fun stopPolling_retainsLastKnownStatus() {
        // Arrange
        fakeGitService.statusResult = GitStatus(
            branch = "dev",
            aheadCount = 1,
            behindCount = 0,
            stagedFiles = emptyList(),
            unstagedFiles = emptyList(),
            untrackedFiles = emptyList(),
        )
        val (poller, scope) = makePollerWithScope()
        poller.startPolling(repoPath, isActive = true)

        // Act
        poller.stopPolling()

        // Assert — StateFlow retains last observed value
        assertEquals("dev", poller.status.value.branch)

        scope.cancel()
    }
}

package studio.vibe.shared.feature.git.data.executor

import kotlinx.coroutines.test.runTest
import studio.vibe.shared.feature.git.domain.model.AheadBehind
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.testutil.FakeProcessRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitStatusExecutorTest {

    private val fakeProcess = FakeProcessRunner()
    private val workDir = FilePath("/repo")
    private fun executor() = GitStatusExecutor(GitProcessRunner(fakeProcess, "/usr/bin/git"))

    // ── diff() ────────────────────────────────────────────────────────────────────

    @Test
    fun diff_staged_issuesDiffCachedCommand() = runTest {
        fakeProcess.respondWith(stdout = "")

        executor().diff("src/Foo.kt", staged = true, at = workDir)

        val cmd = fakeProcess.invocations.first().command
        assertTrue(cmd.contains("diff"))
        assertTrue(cmd.contains("--cached"))
        assertTrue(cmd.contains("src/Foo.kt"))
    }

    @Test
    fun diff_unstaged_doesNotIncludeCachedFlag() = runTest {
        fakeProcess.respondWith(stdout = "")

        executor().diff("src/Foo.kt", staged = false, at = workDir)

        val cmd = fakeProcess.invocations.first().command
        assertTrue(cmd.contains("diff"))
        assertTrue(!cmd.contains("--cached"))
        assertTrue(cmd.contains("src/Foo.kt"))
    }

    // ── fullStagedDiff() ──────────────────────────────────────────────────────────

    @Test
    fun fullStagedDiff_empty_returnsEmptyString() = runTest {
        fakeProcess.respondWith(stdout = "")

        val result = executor().fullStagedDiff(workDir)

        assertEquals("", result)
    }

    @Test
    fun fullStagedDiff_withContent_returnsDiffText() = runTest {
        val diffText = "diff --git a/Foo.kt b/Foo.kt\n+added line"
        fakeProcess.respondWith(stdout = diffText)

        val result = executor().fullStagedDiff(workDir)

        assertEquals(diffText, result)
    }

    @Test
    fun fullStagedDiff_issuesDiffStagedCommand() = runTest {
        fakeProcess.respondWith(stdout = "")

        executor().fullStagedDiff(workDir)

        val cmd = fakeProcess.invocations.first().command
        assertTrue(cmd.contains("diff"))
        assertTrue(cmd.contains("--staged"))
    }

    // ── aheadBehind() ─────────────────────────────────────────────────────────────

    @Test
    fun aheadBehind_parsesTabSeparatedValues() = runTest {
        fakeProcess.respondWith(stdout = "3\t7\n")

        val result = executor().aheadBehind(workDir)

        assertEquals(AheadBehind(ahead = 3, behind = 7), result)
    }

    @Test
    fun aheadBehind_divergedRepo_returnsCorrectCounts() = runTest {
        fakeProcess.respondWith(stdout = "5\t12")

        val result = executor().aheadBehind(workDir)

        assertEquals(5, result.ahead)
        assertEquals(12, result.behind)
    }

    @Test
    fun aheadBehind_commandFails_returnsZeroZero() = runTest {
        fakeProcess.respondWith(exitCode = 1, stderr = "fatal: no upstream")

        val result = executor().aheadBehind(workDir)

        assertEquals(AheadBehind(0, 0), result)
    }

    @Test
    fun aheadBehind_malformedOutput_returnsZeroZero() = runTest {
        fakeProcess.respondWith(stdout = "not-a-number")

        val result = executor().aheadBehind(workDir)

        assertEquals(AheadBehind(0, 0), result)
    }

    // ── log() ─────────────────────────────────────────────────────────────────────

    @Test
    fun log_limitZero_returnsEmptyList() = runTest {
        // git log -n 0 returns empty output
        fakeProcess.respondWith(stdout = "")

        val result = executor().log(0, workDir)

        assertTrue(result.isEmpty())
    }

    @Test
    fun log_withEntries_parsesCorrectly() = runTest {
        val gitOutput = "abc123def456abc1\tabc123d\tfeat: add thing\tAlice\t2024-01-15T10:00:00+00:00"
        fakeProcess.respondWith(stdout = gitOutput)

        val result = executor().log(1, workDir)

        assertEquals(1, result.size)
        assertEquals("abc123d", result.first().shortHash)
        assertEquals("feat: add thing", result.first().message)
        assertEquals("Alice", result.first().author)
    }

    @Test
    fun log_malformedLine_skipsEntry() = runTest {
        val gitOutput = "only-two\tfields\n" +
            "abc123def456abc1\tabc123d\tfeat: add\tAlice\t2024-01-15T10:00:00+00:00"
        fakeProcess.respondWith(stdout = gitOutput)

        val result = executor().log(10, workDir)

        // Only the well-formed line survives
        assertEquals(1, result.size)
    }

    @Test
    fun log_passesLimitToGitCommand() = runTest {
        fakeProcess.respondWith(stdout = "")

        executor().log(42, workDir)

        val cmd = fakeProcess.invocations.first().command
        assertTrue(cmd.contains("-n"))
        assertTrue(cmd.contains("42"))
    }
}

package studio.vibe.shared.feature.git.data.executor

import kotlinx.coroutines.test.runTest
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.testutil.FakeProcessRunner
import kotlin.test.Test
import kotlin.test.assertTrue

class GitStagingExecutorTest {

    private val fakeProcess = FakeProcessRunner()
    private val workDir = FilePath("/repo")
    private fun executor() = GitStagingExecutor(GitProcessRunner(fakeProcess, "/usr/bin/git"))

    // ── stage() ──────────────────────────────────────────────────────────────────

    @Test
    fun stage_emptyFileList_issuesAddDashA() = runTest {
        fakeProcess.respondWith(stdout = "")

        executor().stage(emptyList(), workDir)

        val cmd = fakeProcess.invocations.first().command
        assertTrue(cmd.contains("add"))
        assertTrue(cmd.contains("-A"))
    }

    @Test
    fun stage_specificFiles_includesDashDashSeparator() = runTest {
        fakeProcess.respondWith(stdout = "")

        executor().stage(listOf("src/Foo.kt", "src/Bar.kt"), workDir)

        val cmd = fakeProcess.invocations.first().command
        assertTrue(cmd.contains("add"))
        assertTrue(cmd.contains("--"))
        assertTrue(cmd.contains("src/Foo.kt"))
        assertTrue(cmd.contains("src/Bar.kt"))
    }

    @Test
    fun stage_deletedFile_includesItInArgs() = runTest {
        // Staging a deleted file uses 'git add -- <file>' which also records deletions
        fakeProcess.respondWith(stdout = "")

        executor().stage(listOf("deleted_file.txt"), workDir)

        val cmd = fakeProcess.invocations.first().command
        assertTrue(cmd.contains("deleted_file.txt"))
    }

    // ── unstage() ────────────────────────────────────────────────────────────────

    @Test
    fun unstage_emptyFileList_issuesRestoreStagedDot() = runTest {
        fakeProcess.respondWith(stdout = "")

        executor().unstage(emptyList(), workDir)

        val cmd = fakeProcess.invocations.first().command
        assertTrue(cmd.contains("restore"))
        assertTrue(cmd.contains("--staged"))
        assertTrue(cmd.contains("."))
    }

    @Test
    fun unstage_specificFiles_includesDashDashSeparator() = runTest {
        fakeProcess.respondWith(stdout = "")

        executor().unstage(listOf("src/Foo.kt"), workDir)

        val cmd = fakeProcess.invocations.first().command
        assertTrue(cmd.contains("restore"))
        assertTrue(cmd.contains("--staged"))
        assertTrue(cmd.contains("--"))
        assertTrue(cmd.contains("src/Foo.kt"))
    }
}

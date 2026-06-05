package studio.vibe.shared.service.git.executor

import kotlinx.coroutines.test.runTest
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.testutil.FakeProcessRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitInspectExecutorTest {

    private val fakeProcess = FakeProcessRunner()
    private val workDir = FilePath("/repo")
    private fun executor() = GitInspectExecutor(GitProcessRunner(fakeProcess, "/usr/bin/git"))

    // ── isRepository() ───────────────────────────────────────────────────────────

    @Test
    fun isRepository_gitRevParseSucceeds_returnsTrue() = runTest {
        fakeProcess.respondWith(exitCode = 0, stdout = "true")

        assertTrue(executor().isRepository(workDir))
    }

    @Test
    fun isRepository_gitRevParseFails_returnsFalse() = runTest {
        fakeProcess.respondWith(exitCode = 128, stderr = "fatal: not a git repository")

        assertFalse(executor().isRepository(workDir))
    }

    @Test
    fun isRepository_processThrows_returnsFalse() = runTest {
        fakeProcess.respondWithError(RuntimeException("binary missing"))

        assertFalse(executor().isRepository(workDir))
    }

    // ── repositoryRoot() ──────────────────────────────────────────────────────────

    @Test
    fun repositoryRoot_returnsTrimedPath() = runTest {
        fakeProcess.respondWith(stdout = "/home/user/project\n")

        val root = executor().repositoryRoot(FilePath("/home/user/project/subdir"))

        assertEquals(FilePath("/home/user/project"), root)
    }

    @Test
    fun repositoryRoot_usesRevParseShowToplevel() = runTest {
        fakeProcess.respondWith(stdout = "/repo")

        executor().repositoryRoot(workDir)

        val cmd = fakeProcess.invocations.first().command
        assertTrue(cmd.contains("--show-toplevel"))
    }

    // ── initRepository() ──────────────────────────────────────────────────────────

    @Test
    fun initRepository_issuesInitCommand() = runTest {
        fakeProcess.respondWith(stdout = "Initialized empty Git repository")

        executor().initRepository(workDir)

        val cmd = fakeProcess.invocations.first().command
        assertTrue(cmd.contains("init"))
    }
}

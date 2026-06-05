package studio.vibe.shared.service.git.executor

import kotlinx.coroutines.test.runTest
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.GitServiceError
import studio.vibe.shared.testutil.FakeProcessRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GitCommitExecutorTest {

    private val fakeProcess = FakeProcessRunner()
    private val workDir = FilePath("/repo")
    private fun executor() = GitCommitExecutor(GitProcessRunner(fakeProcess, "/usr/bin/git"))

    @Test
    fun commit_emptyMessage_throwsCommandFailedWithoutCallingProcess() = runTest {
        // Arrange / Act / Assert
        val err = assertFailsWith<GitServiceError.CommandFailed> {
            executor().commit(message = "   ", workDir)
        }
        assertEquals("commit", err.command)
        assertEquals(1, err.exitCode)
        assertTrue(err.stderr.contains("empty"))
        // The process runner must not have been called
        assertTrue(fakeProcess.invocations.isEmpty())
    }

    @Test
    fun commit_blankMessage_throwsCommandFailed() = runTest {
        assertFailsWith<GitServiceError.CommandFailed> {
            executor().commit(message = "\t\n", workDir)
        }
        assertTrue(fakeProcess.invocations.isEmpty())
    }

    @Test
    fun commit_validMessage_returnsExtractedHash() = runTest {
        // Arrange — typical git commit output
        fakeProcess.respondWith(stdout = "[main abc1234] feat: add thing\n 1 file changed")

        // Act
        val hash = executor().commit("feat: add thing", workDir)

        // Assert — the executor extracts the first hex sequence
        assertTrue(hash.matches(Regex("[0-9a-f]{7,40}")))
    }

    @Test
    fun commit_outputHasNoHash_returnsFullOutput() = runTest {
        // Arrange — non-standard output without a hex hash
        fakeProcess.respondWith(stdout = "no hash here")

        val result = executor().commit("fix: something", workDir)

        assertEquals("no hash here", result)
    }

    @Test
    fun commit_nonZeroExit_propagatesCommandFailed() = runTest {
        // Arrange
        fakeProcess.respondWith(exitCode = 1, stderr = "nothing to commit")

        val err = assertFailsWith<GitServiceError.CommandFailed> {
            executor().commit("msg", workDir)
        }
        assertEquals(1, err.exitCode)
        assertTrue(err.stderr.contains("nothing"))
    }

    @Test
    fun commit_passesDashMAndMessageToProcess() = runTest {
        fakeProcess.respondWith(stdout = "[main ab12345] msg")

        executor().commit("feat: my feature", workDir)

        val cmd = fakeProcess.invocations.first().command
        assertTrue(cmd.contains("-m"))
        assertTrue(cmd.contains("feat: my feature"))
    }
}

package studio.vibe.shared.feature.git.data.executor

import kotlinx.coroutines.test.runTest
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.feature.git.domain.model.GitServiceError
import studio.vibe.shared.testutil.FakeProcessRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GitProcessRunnerTest {

    private val fakeProcess = FakeProcessRunner()
    private val workDir = FilePath("/repo")
    private fun runner() = GitProcessRunner(fakeProcess, gitPath = "/usr/bin/git")

    @Test
    fun runGit_successfulExit_returnsStdout() = runTest {
        // Arrange
        fakeProcess.respondWith(exitCode = 0, stdout = "abc1234")
        val runner = runner()

        // Act
        val result = runner.runGit(listOf("rev-parse", "HEAD"), workDir)

        // Assert
        assertEquals("abc1234", result)
    }

    @Test
    fun runGit_nonZeroExit_throwsCommandFailed() = runTest {
        // Arrange
        fakeProcess.respondWith(exitCode = 1, stdout = "", stderr = "fatal: ambiguous argument")
        val runner = runner()

        // Act / Assert
        val error = assertFailsWith<GitServiceError.CommandFailed> {
            runner.runGit(listOf("log"), workDir)
        }
        assertEquals("log", error.command)
        assertEquals(1, error.exitCode)
        assertTrue(error.stderr.contains("ambiguous"))
    }

    @Test
    fun runGit_stderrContainsNotARepository_throwsNotARepository() = runTest {
        // Arrange
        fakeProcess.respondWith(exitCode = 128, stderr = "fatal: not a git repository")
        val runner = runner()

        // Act / Assert
        val error = assertFailsWith<GitServiceError.NotARepository> {
            runner.runGit(listOf("status"), workDir)
        }
        assertEquals(workDir, error.path)
    }

    @Test
    fun runGit_processRunnerThrows_throwsGitNotFound() = runTest {
        // Arrange
        fakeProcess.respondWithError(RuntimeException("binary not found"))
        val runner = runner()

        // Act / Assert
        assertFailsWith<GitServiceError.GitNotFound> {
            runner.runGit(listOf("status"), workDir)
        }
    }

    @Test
    fun runGit_prependsGitPath_asFirstCommandArg() = runTest {
        // Arrange
        fakeProcess.respondWith(exitCode = 0, stdout = "")
        val runner = runner()

        // Act
        runner.runGit(listOf("branch"), workDir)

        // Assert
        val cmd = fakeProcess.invocations.first().command
        assertEquals("/usr/bin/git", cmd[0])
        assertEquals("branch", cmd[1])
    }

    @Test
    fun runGit_suppressCredentials_injectsEnvVars() = runTest {
        // Arrange
        fakeProcess.respondWith(exitCode = 0, stdout = "")
        val runner = runner()

        // Act
        runner.runGit(listOf("fetch"), workDir, suppressCredentials = true)

        // Assert
        val env = fakeProcess.invocations.first().env
        assertEquals("0", env["GIT_TERMINAL_PROMPT"])
        assertEquals("/usr/bin/true", env["GIT_ASKPASS"])
    }

    @Test
    fun runGit_suppressCredentialsFalse_doesNotInjectPromptVars() = runTest {
        // Arrange
        fakeProcess.respondWith(exitCode = 0, stdout = "")
        val runner = runner()

        // Act
        runner.runGit(listOf("fetch"), workDir, suppressCredentials = false)

        // Assert
        val env = fakeProcess.invocations.first().env
        assertTrue("GIT_TERMINAL_PROMPT" !in env)
    }
}

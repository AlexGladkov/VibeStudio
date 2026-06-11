package studio.vibe.shared.feature.git.data.executor

import kotlinx.coroutines.test.runTest
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.feature.git.domain.model.GitServiceError
import studio.vibe.shared.testutil.FakeProcessRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitBranchExecutorTest {

    private val fakeProcess = FakeProcessRunner()
    private val workDir = FilePath("/repo")
    private fun executor() = GitBranchExecutor(GitProcessRunner(fakeProcess, "/usr/bin/git"))

    // ── branches() ───────────────────────────────────────────────────────────────

    @Test
    fun branches_parseLocalBranches_returnsCorrectList() = runTest {
        // Arrange — first call = local, second = remote (no remote refs)
        fakeProcess.respondWith(stdout = "main\t*\nfeature/foo\t ")
        fakeProcess.respondWith(stdout = "")

        // Act
        val branches = executor().branches(workDir)

        // Assert
        assertEquals(2, branches.size)
        val main = branches.first { it.name == "main" }
        assertTrue(main.isCurrent)
        assertFalse(main.isRemote)
        val feat = branches.first { it.name == "feature/foo" }
        assertFalse(feat.isCurrent)
    }

    @Test
    fun branches_parsesRemoteBranches_appendsToList() = runTest {
        // Arrange
        fakeProcess.respondWith(stdout = "main\t*")
        fakeProcess.respondWith(stdout = "origin/main\norigin/HEAD")

        // Act
        val branches = executor().branches(workDir)

        // Assert — origin/HEAD should be filtered out
        val remotes = branches.filter { it.isRemote }
        assertEquals(1, remotes.size)
        assertEquals("origin/main", remotes.first().name)
    }

    @Test
    fun branches_remoteCallFails_returnsLocalBranchesOnly() = runTest {
        // Arrange
        fakeProcess.respondWith(stdout = "main\t*")
        fakeProcess.respondWith(exitCode = 1, stderr = "fatal: not a git repository")

        // Act — must not throw, remote failure is silently ignored
        val branches = executor().branches(workDir)

        // Assert
        assertEquals(1, branches.size)
        assertEquals("main", branches.first().name)
    }

    @Test
    fun branches_emptyRepo_returnsEmptyList() = runTest {
        fakeProcess.respondWith(stdout = "")
        fakeProcess.respondWith(stdout = "")

        val branches = executor().branches(workDir)

        assertTrue(branches.isEmpty())
    }

    // ── checkout() ───────────────────────────────────────────────────────────────

    @Test
    fun checkout_validBranch_issuesSwitchCommand() = runTest {
        fakeProcess.respondWith(stdout = "")

        executor().checkout("feature/bar", workDir)

        val cmd = fakeProcess.invocations.first().command
        assertTrue(cmd.contains("switch"))
        assertTrue(cmd.contains("feature/bar"))
    }

    @Test
    fun checkout_invalidBranchName_throwsCommandFailed() = runTest {
        // Branch names starting with '-' are invalid (injection risk)
        assertFailsWith<GitServiceError.CommandFailed> {
            executor().checkout("--upload-pack=evil", workDir)
        }
        // No process call should have been made
        assertTrue(fakeProcess.invocations.isEmpty())
    }

    // ── createBranch() ────────────────────────────────────────────────────────────

    @Test
    fun createBranch_noFrom_issuesSwitchDashC() = runTest {
        fakeProcess.respondWith(stdout = "")

        executor().createBranch("new-feature", from = null, at = workDir)

        val cmd = fakeProcess.invocations.first().command
        assertTrue(cmd.contains("-c"))
        assertTrue(cmd.contains("new-feature"))
        assertFalse(cmd.contains("from"))
    }

    @Test
    fun createBranch_withFrom_includesBaseInCommand() = runTest {
        fakeProcess.respondWith(stdout = "")

        executor().createBranch("hotfix", from = "main", at = workDir)

        val cmd = fakeProcess.invocations.first().command
        assertTrue(cmd.contains("main"))
    }

    @Test
    fun createBranch_invalidName_throwsWithoutCallingProcess() = runTest {
        assertFailsWith<GitServiceError.CommandFailed> {
            executor().createBranch("bad name with spaces", from = null, at = workDir)
        }
        assertTrue(fakeProcess.invocations.isEmpty())
    }

    // ── deleteBranch() ────────────────────────────────────────────────────────────

    @Test
    fun deleteBranch_forceTrue_usesDashCapitalD() = runTest {
        fakeProcess.respondWith(stdout = "")

        executor().deleteBranch("old-branch", force = true, at = workDir)

        val cmd = fakeProcess.invocations.first().command
        assertTrue(cmd.contains("-D"))
        assertFalse(cmd.contains("-d"))
    }

    @Test
    fun deleteBranch_forceFalse_usesDashLowerD() = runTest {
        fakeProcess.respondWith(stdout = "")

        executor().deleteBranch("old-branch", force = false, at = workDir)

        val cmd = fakeProcess.invocations.first().command
        assertTrue(cmd.contains("-d"))
        assertFalse(cmd.contains("-D"))
    }

    @Test
    fun deleteBranch_gitRejectsUnmerged_propagatesCommandFailed() = runTest {
        fakeProcess.respondWith(exitCode = 1, stderr = "error: The branch 'old' is not fully merged")

        val err = assertFailsWith<GitServiceError.CommandFailed> {
            executor().deleteBranch("old", force = false, at = workDir)
        }
        assertEquals(1, err.exitCode)
    }
}

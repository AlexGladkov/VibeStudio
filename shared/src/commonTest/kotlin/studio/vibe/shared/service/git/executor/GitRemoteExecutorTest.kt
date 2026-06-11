package studio.vibe.shared.feature.git.data.executor

import kotlinx.coroutines.test.runTest
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.feature.git.domain.model.GitServiceError
import studio.vibe.shared.testutil.FakeProcessRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitRemoteExecutorTest {

    private val fakeProcess = FakeProcessRunner()
    private val workDir = FilePath("/repo")
    private fun executor() = GitRemoteExecutor(GitProcessRunner(fakeProcess, "/usr/bin/git"))

    // ── push() ───────────────────────────────────────────────────────────────────

    @Test
    fun push_happy_issuesPushCommand() = runTest {
        fakeProcess.respondWith(stdout = "")

        executor().push("origin", workDir)

        val cmd = fakeProcess.invocations.first().command
        assertTrue(cmd.contains("push"))
        assertTrue(cmd.contains("origin"))
    }

    @Test
    fun push_rejectedStderr_throwsPushRejected() = runTest {
        fakeProcess.respondWith(exitCode = 1, stderr = "! [rejected] main -> main (non-fast-forward)")

        val err = assertFailsWith<GitServiceError.PushRejected> {
            executor().push("origin", workDir)
        }
        assertTrue(err.reason.contains("rejected"))
    }

    @Test
    fun push_invalidRemoteName_throwsCommandFailed() = runTest {
        assertFailsWith<GitServiceError.CommandFailed> {
            executor().push("--evil", workDir)
        }
        assertTrue(fakeProcess.invocations.isEmpty())
    }

    // ── pull() ────────────────────────────────────────────────────────────────────

    @Test
    fun pull_happy_issuesPullCommand() = runTest {
        fakeProcess.respondWith(stdout = "Already up to date.")

        executor().pull("origin", workDir)

        val cmd = fakeProcess.invocations.first().command
        assertTrue(cmd.contains("pull"))
    }

    @Test
    fun pull_conflictStderr_throwsMergeConflict() = runTest {
        fakeProcess.respondWith(
            exitCode = 1,
            stderr = "CONFLICT (content): Merge conflict in src/Foo.kt\nAutomatic merge failed",
        )

        val err = assertFailsWith<GitServiceError.MergeConflict> {
            executor().pull("origin", workDir)
        }
        assertEquals(1, err.files.size)
        assertTrue(err.files.first().contains("CONFLICT"))
    }

    // ── fetch() ───────────────────────────────────────────────────────────────────

    @Test
    fun fetch_happy_issuesFetchCommand() = runTest {
        fakeProcess.respondWith(stdout = "")

        executor().fetch("origin", workDir)

        val cmd = fakeProcess.invocations.first().command
        assertTrue(cmd.contains("fetch"))
        assertTrue(cmd.contains("origin"))
    }

    // ── pushBranch() ──────────────────────────────────────────────────────────────

    @Test
    fun pushBranch_happy_includesSetUpstreamFlag() = runTest {
        fakeProcess.respondWith(stdout = "")

        executor().pushBranch("feature/foo", "origin", workDir)

        val cmd = fakeProcess.invocations.first().command
        assertTrue(cmd.contains("--set-upstream"))
        assertTrue(cmd.contains("feature/foo"))
        assertTrue(cmd.contains("origin"))
    }

    @Test
    fun pushBranch_rejected_throwsPushRejected() = runTest {
        fakeProcess.respondWith(exitCode = 1, stderr = "! [rejected] tip")

        assertFailsWith<GitServiceError.PushRejected> {
            executor().pushBranch("feature/foo", "origin", workDir)
        }
    }

    // ── pullBranch() ──────────────────────────────────────────────────────────────

    @Test
    fun pullBranch_currentBranch_issuesPullRemote() = runTest {
        fakeProcess.respondWith(stdout = "")

        executor().pullBranch("main", isCurrent = true, remote = "origin", at = workDir)

        val cmd = fakeProcess.invocations.first().command
        assertTrue(cmd.contains("pull"))
        assertTrue(cmd.contains("origin"))
    }

    @Test
    fun pullBranch_notCurrentBranch_issuesFetchRefSpec() = runTest {
        fakeProcess.respondWith(stdout = "")

        executor().pullBranch("feature/foo", isCurrent = false, remote = "origin", at = workDir)

        val cmd = fakeProcess.invocations.first().command
        assertTrue(cmd.contains("fetch"))
        assertTrue(cmd.any { it.contains("feature/foo") })
    }

    @Test
    fun pullBranch_conflictOnCurrent_throwsMergeConflict() = runTest {
        fakeProcess.respondWith(exitCode = 1, stderr = "CONFLICT (content): Merge conflict in A.kt")

        assertFailsWith<GitServiceError.MergeConflict> {
            executor().pullBranch("main", isCurrent = true, remote = "origin", at = workDir)
        }
    }

    // ── addRemote() ───────────────────────────────────────────────────────────────

    @Test
    fun addRemote_validInputs_issuesRemoteAddCommand() = runTest {
        fakeProcess.respondWith(stdout = "")

        executor().addRemote("origin", "https://github.com/user/repo.git", workDir)

        val cmd = fakeProcess.invocations.first().command
        assertTrue(cmd.contains("remote"))
        assertTrue(cmd.contains("add"))
        assertTrue(cmd.contains("origin"))
    }

    @Test
    fun addRemote_emptyName_throwsCommandFailed() = runTest {
        assertFailsWith<GitServiceError.CommandFailed> {
            executor().addRemote("", "https://github.com/u/r.git", workDir)
        }
        assertTrue(fakeProcess.invocations.isEmpty())
    }

    @Test
    fun addRemote_nameStartsWithDash_throwsCommandFailed() = runTest {
        assertFailsWith<GitServiceError.CommandFailed> {
            executor().addRemote("-dangerous", "https://example.com/r.git", workDir)
        }
    }

    @Test
    fun addRemote_extProtocol_throwsCommandFailed() = runTest {
        assertFailsWith<GitServiceError.CommandFailed> {
            executor().addRemote("evil", "ext::evil-command", workDir)
        }
    }

    @Test
    fun addRemote_emptyUrl_throwsCommandFailed() = runTest {
        assertFailsWith<GitServiceError.CommandFailed> {
            executor().addRemote("origin", "", workDir)
        }
    }

    // ── remoteURL() ───────────────────────────────────────────────────────────────

    @Test
    fun remoteURL_found_returnsUrl() = runTest {
        fakeProcess.respondWith(stdout = "https://github.com/user/repo.git\n")

        val url = executor().remoteURL("origin", workDir)

        assertEquals("https://github.com/user/repo.git", url)
    }

    @Test
    fun remoteURL_commandFails_returnsNull() = runTest {
        fakeProcess.respondWith(exitCode = 2, stderr = "error: No such remote")

        val url = executor().remoteURL("missing", workDir)

        assertNull(url)
    }

    // ── defaultRemote() ───────────────────────────────────────────────────────────

    @Test
    fun defaultRemote_branchConfigPresent_returnsConfiguredRemote() = runTest {
        // First call: branch config
        fakeProcess.respondWith(stdout = "upstream\n")

        val remote = executor().defaultRemote("main", workDir)

        assertEquals("upstream", remote)
    }

    @Test
    fun defaultRemote_noConfig_fallsBackToFirstRemote() = runTest {
        // Branch config not found → empty
        fakeProcess.respondWith(exitCode = 1, stderr = "key not found")
        // remote list
        fakeProcess.respondWith(stdout = "upstream\norigin\n")

        val remote = executor().defaultRemote("main", workDir)

        assertEquals("upstream", remote)
    }

    @Test
    fun defaultRemote_noRemotesAtAll_returnsOrigin() = runTest {
        fakeProcess.respondWith(exitCode = 1, stderr = "")
        fakeProcess.respondWith(exitCode = 1, stderr = "")

        val remote = executor().defaultRemote(null, workDir)

        assertEquals("origin", remote)
    }
}

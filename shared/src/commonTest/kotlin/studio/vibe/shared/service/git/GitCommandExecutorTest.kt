package studio.vibe.shared.service.git

import kotlinx.coroutines.test.runTest
import studio.vibe.shared.model.*
import studio.vibe.shared.testutil.FakeProcessRunner
import kotlin.test.*

/**
 * Unit tests for [GitCommandExecutor].
 *
 * All subprocess execution is stubbed via [FakeProcessRunner].
 * Tests cover:
 * - parseStatus (internal)
 * - parseFileStatus (internal)
 * - parseDiff (internal)
 * - parseNumstat (internal)
 * - validateBranchName (internal)
 * - addRemote URL validation
 * - commit message validation
 */
class GitCommandExecutorTest {

    private lateinit var processRunner: FakeProcessRunner
    private lateinit var executor: GitCommandExecutor
    private val repoPath = FilePath("/repo")

    @BeforeTest
    fun setup() {
        processRunner = FakeProcessRunner()
        executor = GitCommandExecutor(processRunner = processRunner, gitPath = "git")
    }

    @AfterTest
    fun teardown() {
        processRunner.reset()
    }

    // ── parseStatus ───────────────────────────────────────────────────────────

    @Test
    fun parseStatus_cleanRepo_returnsEmptyStatus() {
        // Arrange
        val output = "## main...origin/main\n"

        // Act
        val status = executor.parseStatus(output)

        // Assert
        assertEquals("main", status.branch)
        assertEquals(0, status.aheadCount)
        assertEquals(0, status.behindCount)
        assertTrue(status.stagedFiles.isEmpty())
        assertTrue(status.unstagedFiles.isEmpty())
        assertTrue(status.untrackedFiles.isEmpty())
        assertTrue(status.isClean)
    }

    @Test
    fun parseStatus_branchWithAheadBehind_parsesCorrectly() {
        // Arrange
        val output = "## feature/foo...origin/feature/foo [ahead 3, behind 1]\n"

        // Act
        val status = executor.parseStatus(output)

        // Assert
        assertEquals("feature/foo", status.branch)
        assertEquals(3, status.aheadCount)
        assertEquals(1, status.behindCount)
    }

    @Test
    fun parseStatus_onlyAhead_parsesAheadCount() {
        // Arrange
        val output = "## main...origin/main [ahead 2]\n"

        // Act
        val status = executor.parseStatus(output)

        // Assert
        assertEquals(2, status.aheadCount)
        assertEquals(0, status.behindCount)
    }

    @Test
    fun parseStatus_onlyBehind_parsesBehindCount() {
        // Arrange
        val output = "## main...origin/main [behind 5]\n"

        // Act
        val status = executor.parseStatus(output)

        // Assert
        assertEquals(0, status.aheadCount)
        assertEquals(5, status.behindCount)
    }

    @Test
    fun parseStatus_modifiedStagedFile_appearsInStagedFiles() {
        // Arrange — 'M' in index column, ' ' in worktree
        val output = "## main\nM  src/Foo.kt\n"

        // Act
        val status = executor.parseStatus(output)

        // Assert
        assertEquals(1, status.stagedFiles.size)
        assertEquals("src/Foo.kt", status.stagedFiles[0].path)
        assertEquals(GitFileStatus.MODIFIED, status.stagedFiles[0].status)
        assertTrue(status.unstagedFiles.isEmpty())
    }

    @Test
    fun parseStatus_modifiedUnstagedFile_appearsInUnstagedFiles() {
        // Arrange — ' ' in index column, 'M' in worktree
        val output = "## main\n M src/Bar.kt\n"

        // Act
        val status = executor.parseStatus(output)

        // Assert
        assertEquals(1, status.unstagedFiles.size)
        assertEquals("src/Bar.kt", status.unstagedFiles[0].path)
        assertEquals(GitFileStatus.MODIFIED, status.unstagedFiles[0].status)
        assertTrue(status.stagedFiles.isEmpty())
    }

    @Test
    fun parseStatus_untrackedFile_appearsInUntrackedFiles() {
        // Arrange — '??' prefix
        val output = "## main\n?? new_file.kt\n"

        // Act
        val status = executor.parseStatus(output)

        // Assert
        assertEquals(1, status.untrackedFiles.size)
        assertEquals("new_file.kt", status.untrackedFiles[0].path)
        assertEquals(GitFileStatus.UNTRACKED, status.untrackedFiles[0].status)
    }

    @Test
    fun parseStatus_addedDeletedRenamedCopied_parsedForAllStatuses() {
        // Arrange
        val output = buildString {
            appendLine("## main")
            appendLine("A  added.kt")
            appendLine("D  deleted.kt")
            appendLine("R  renamed.kt")
            appendLine("C  copied.kt")
        }

        // Act
        val status = executor.parseStatus(output)

        // Assert
        val stagedStatuses = status.stagedFiles.map { it.status }
        assertContains(stagedStatuses, GitFileStatus.ADDED)
        assertContains(stagedStatuses, GitFileStatus.DELETED)
        assertContains(stagedStatuses, GitFileStatus.RENAMED)
        assertContains(stagedStatuses, GitFileStatus.COPIED)
    }

    @Test
    fun parseStatus_bothStagedAndUnstaged_fileAppearsInBothLists() {
        // Arrange — 'M' in both index and worktree columns
        val output = "## main\nMM src/Mixed.kt\n"

        // Act
        val status = executor.parseStatus(output)

        // Assert
        assertEquals(1, status.stagedFiles.size)
        assertEquals(1, status.unstagedFiles.size)
        assertEquals("src/Mixed.kt", status.stagedFiles[0].path)
        assertEquals("src/Mixed.kt", status.unstagedFiles[0].path)
    }

    @Test
    fun parseStatus_emptyOutput_returnsEmptyStatus() {
        // Arrange + Act
        val status = executor.parseStatus("")

        // Assert
        assertEquals("", status.branch)
        assertTrue(status.isClean)
    }

    // ── parseFileStatus ───────────────────────────────────────────────────────

    @Test
    fun parseFileStatus_knownCharacters_returnCorrectStatuses() {
        assertEquals(GitFileStatus.MODIFIED, executor.parseFileStatus('M'))
        assertEquals(GitFileStatus.ADDED, executor.parseFileStatus('A'))
        assertEquals(GitFileStatus.DELETED, executor.parseFileStatus('D'))
        assertEquals(GitFileStatus.RENAMED, executor.parseFileStatus('R'))
        assertEquals(GitFileStatus.COPIED, executor.parseFileStatus('C'))
    }

    @Test
    fun parseFileStatus_unknownCharacter_returnsNull() {
        assertNull(executor.parseFileStatus('X'))
        assertNull(executor.parseFileStatus('?'))
        assertNull(executor.parseFileStatus(' '))
    }

    // ── parseDiff ─────────────────────────────────────────────────────────────

    @Test
    fun parseDiff_emptyOutput_returnsEmptyList() {
        // Arrange + Act
        val hunks = executor.parseDiff("")

        // Assert
        assertTrue(hunks.isEmpty())
    }

    @Test
    fun parseDiff_singleHunk_returnsOneHunk() {
        // Arrange
        val output = """
            @@ -1,3 +1,4 @@
             context line
            -deleted line
            +added line
             another context
        """.trimIndent()

        // Act
        val hunks = executor.parseDiff(output)

        // Assert
        assertEquals(1, hunks.size)
        val hunk = hunks[0]
        assertTrue(hunk.header.startsWith("@@"))
    }

    @Test
    fun parseDiff_singleHunk_correctLineTypes() {
        // Arrange
        val output = """
            @@ -1,3 +1,4 @@
             context line
            -deleted line
            +added line
        """.trimIndent()

        // Act
        val hunks = executor.parseDiff(output)
        val lines = hunks[0].lines

        // Assert
        val types = lines.map { it.type }
        assertContains(types, DiffLineType.CONTEXT)
        assertContains(types, DiffLineType.DELETION)
        assertContains(types, DiffLineType.ADDITION)
    }

    @Test
    fun parseDiff_multipleHunks_returnsAllHunks() {
        // Arrange
        val output = buildString {
            appendLine("@@ -1,2 +1,2 @@")
            appendLine(" context")
            appendLine("-old line 1")
            appendLine("+new line 1")
            appendLine("@@ -10,2 +10,2 @@")
            appendLine(" context 2")
            appendLine("-old line 2")
            appendLine("+new line 2")
        }

        // Act
        val hunks = executor.parseDiff(output)

        // Assert
        assertEquals(2, hunks.size)
    }

    @Test
    fun parseDiff_additionLine_hasCorrectNewLineNumber() {
        // Arrange — hunk starts at new line 5
        val output = "@@ -5,1 +5,2 @@\n+new line\n"

        // Act
        val hunk = executor.parseDiff(output)[0]
        val addedLine = hunk.lines.first { it.type == DiffLineType.ADDITION }

        // Assert
        assertEquals(5, addedLine.newLineNumber)
        assertNull(addedLine.oldLineNumber)
    }

    @Test
    fun parseDiff_deletionLine_hasCorrectOldLineNumber() {
        // Arrange
        val output = "@@ -3,1 +3,0 @@\n-removed line\n"

        // Act
        val hunk = executor.parseDiff(output)[0]
        val deletedLine = hunk.lines.first { it.type == DiffLineType.DELETION }

        // Assert
        assertEquals(3, deletedLine.oldLineNumber)
        assertNull(deletedLine.newLineNumber)
    }

    @Test
    fun parseDiff_contentStrippedOfLeadingSignChar() {
        // Arrange
        val output = "@@ -1,1 +1,1 @@\n+hello world\n"

        // Act
        val line = executor.parseDiff(output)[0].lines.first()

        // Assert — the leading '+' is stripped from content
        assertEquals("hello world", line.content)
    }

    // ── parseNumstat ──────────────────────────────────────────────────────────

    @Test
    fun parseNumstat_typicalOutput_returnsParsedStats() {
        // Arrange
        val output = "5\t3\tsrc/Main.kt\n10\t0\tsrc/New.kt\n"

        // Act
        val stats = executor.parseNumstat(output)

        // Assert
        assertEquals(2, stats.size)
        assertEquals(GitDiffStat(added = 5, deleted = 3), stats["src/Main.kt"])
        assertEquals(GitDiffStat(added = 10, deleted = 0), stats["src/New.kt"])
    }

    @Test
    fun parseNumstat_binaryFile_skipped() {
        // Arrange — binary files show '-' for counts
        val output = "-\t-\tbinary.png\n2\t1\ttext.kt\n"

        // Act
        val stats = executor.parseNumstat(output)

        // Assert
        assertNull(stats["binary.png"])
        assertNotNull(stats["text.kt"])
    }

    @Test
    fun parseNumstat_emptyOutput_returnsEmpty() {
        assertEquals(emptyMap(), executor.parseNumstat(""))
    }

    // ── validateBranchName ────────────────────────────────────────────────────

    @Test
    fun validateBranchName_validName_doesNotThrow() {
        // Arrange + Act + Assert — no exception
        executor.validateBranchName("main")
        executor.validateBranchName("feature/my-feature")
        executor.validateBranchName("release-1.0")
        executor.validateBranchName("v2.0.0")
    }

    @Test
    fun validateBranchName_emptyName_throws() {
        assertFailsWith<GitServiceError.CommandFailed> {
            executor.validateBranchName("")
        }
    }

    @Test
    fun validateBranchName_leadingDash_throws() {
        assertFailsWith<GitServiceError.CommandFailed> {
            executor.validateBranchName("-bad-name")
        }
    }

    @Test
    fun validateBranchName_doubleDots_throws() {
        assertFailsWith<GitServiceError.CommandFailed> {
            executor.validateBranchName("branch..name")
        }
    }

    @Test
    fun validateBranchName_containsSpace_throws() {
        assertFailsWith<GitServiceError.CommandFailed> {
            executor.validateBranchName("branch name")
        }
    }

    @Test
    fun validateBranchName_containsTilde_throws() {
        assertFailsWith<GitServiceError.CommandFailed> {
            executor.validateBranchName("branch~1")
        }
    }

    @Test
    fun validateBranchName_containsCaret_throws() {
        assertFailsWith<GitServiceError.CommandFailed> {
            executor.validateBranchName("branch^HEAD")
        }
    }

    @Test
    fun validateBranchName_containsColon_throws() {
        assertFailsWith<GitServiceError.CommandFailed> {
            executor.validateBranchName("origin:main")
        }
    }

    @Test
    fun validateBranchName_shellInjectionAttempt_throws() {
        assertFailsWith<GitServiceError.CommandFailed> {
            executor.validateBranchName("main; rm -rf /")
        }
    }

    // ── addRemote URL validation ───────────────────────────────────────────────

    @Test
    fun addRemote_extProtocol_throws() = runTest {
        // Arrange
        processRunner.respondWith(exitCode = 0, stdout = "")

        // Act + Assert — ext:: is a dangerous transport that allows arbitrary command execution
        val ex = assertFailsWith<GitServiceError.CommandFailed> {
            executor.addRemote("origin", "ext::git-upload-pack /path", repoPath)
        }
        assertTrue(ex.stderr.contains("Unsupported remote URL scheme"), "Expected scheme rejection message")
    }

    @Test
    fun addRemote_fdProtocol_throws() = runTest {
        processRunner.respondWith(exitCode = 0, stdout = "")
        assertFailsWith<GitServiceError.CommandFailed> {
            executor.addRemote("origin", "fd::20 /path", repoPath)
        }
    }

    @Test
    fun addRemote_emptyName_throws() = runTest {
        processRunner.respondWith(exitCode = 0, stdout = "")
        assertFailsWith<GitServiceError.CommandFailed> {
            executor.addRemote("", "https://github.com/user/repo.git", repoPath)
        }
    }

    @Test
    fun addRemote_nameBeginsWithDash_throws() = runTest {
        processRunner.respondWith(exitCode = 0, stdout = "")
        assertFailsWith<GitServiceError.CommandFailed> {
            executor.addRemote("-origin", "https://github.com/user/repo.git", repoPath)
        }
    }

    @Test
    fun addRemote_emptyUrl_throws() = runTest {
        processRunner.respondWith(exitCode = 0, stdout = "")
        assertFailsWith<GitServiceError.CommandFailed> {
            executor.addRemote("origin", "", repoPath)
        }
    }

    @Test
    fun addRemote_validHttpsUrl_invokesProcessRunner() = runTest {
        // Arrange
        processRunner.respondWith(exitCode = 0, stdout = "")

        // Act
        executor.addRemote("origin", "https://github.com/user/repo.git", repoPath)

        // Assert — exactly one invocation
        assertEquals(1, processRunner.invocations.size)
        val cmd = processRunner.invocations[0].command
        assertContains(cmd, "remote")
        assertContains(cmd, "add")
        assertContains(cmd, "origin")
        assertContains(cmd, "https://github.com/user/repo.git")
    }

    // ── commit validation ─────────────────────────────────────────────────────

    @Test
    fun commit_emptyMessage_throwsCommandFailed() = runTest {
        processRunner.respondWith(exitCode = 0, stdout = "")
        assertFailsWith<GitServiceError.CommandFailed> {
            executor.commit("", repoPath)
        }
    }

    @Test
    fun commit_blankMessage_throwsCommandFailed() = runTest {
        processRunner.respondWith(exitCode = 0, stdout = "")
        assertFailsWith<GitServiceError.CommandFailed> {
            executor.commit("   ", repoPath)
        }
    }

    @Test
    fun commit_validMessage_invokesGitCommit() = runTest {
        // Arrange
        processRunner.respondWith(exitCode = 0, stdout = "[main abc1234] Add feature\n 1 file changed")

        // Act
        val hash = executor.commit("Add feature", repoPath)

        // Assert
        assertNotNull(hash)
        assertTrue(hash.isNotEmpty())
        val cmd = processRunner.invocations[0].command
        assertContains(cmd, "commit")
        assertContains(cmd, "-m")
        assertContains(cmd, "Add feature")
    }

    // ── runGit error mapping ──────────────────────────────────────────────────

    @Test
    fun status_notARepository_throwsNotARepository() = runTest {
        // Arrange
        processRunner.respondWith(
            exitCode = 128,
            stdout = "",
            stderr = "fatal: not a git repository (or any of the parent directories): .git",
        )

        // Act + Assert
        assertFailsWith<GitServiceError.NotARepository> {
            executor.status(repoPath)
        }
    }

    @Test
    fun status_processRunnerThrows_throwsGitNotFound() = runTest {
        // Arrange
        processRunner.respondWithError(RuntimeException("no such file: git"))

        // Act + Assert
        // GitCommandExecutor catches all exceptions from processRunner.run and re-throws
        // as GitServiceError.GitNotFound (the data object).
        assertFailsWith<GitServiceError.GitNotFound> {
            executor.status(repoPath)
        }
    }
}

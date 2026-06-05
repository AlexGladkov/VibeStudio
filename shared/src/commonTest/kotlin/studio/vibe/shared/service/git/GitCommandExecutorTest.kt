package studio.vibe.shared.service.git

import kotlinx.coroutines.test.runTest
import studio.vibe.shared.model.*
import studio.vibe.shared.service.git.parser.GitBranchParser
import studio.vibe.shared.service.git.parser.GitOutputParser
import studio.vibe.shared.testutil.FakeProcessRunner
import kotlin.test.*

/**
 * Unit tests for [GitCommandExecutor].
 *
 * All subprocess execution is stubbed via [FakeProcessRunner].
 * Tests cover:
 * - GitOutputParser.parseStatus
 * - GitOutputParser.parseFileStatus
 * - GitOutputParser.parseDiff
 * - GitOutputParser.parseNumstat
 * - GitBranchParser.validateBranchName
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
        val status = GitOutputParser.parseStatus(output)

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
        val status = GitOutputParser.parseStatus(output)

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
        val status = GitOutputParser.parseStatus(output)

        // Assert
        assertEquals(2, status.aheadCount)
        assertEquals(0, status.behindCount)
    }

    @Test
    fun parseStatus_onlyBehind_parsesBehindCount() {
        // Arrange
        val output = "## main...origin/main [behind 5]\n"

        // Act
        val status = GitOutputParser.parseStatus(output)

        // Assert
        assertEquals(0, status.aheadCount)
        assertEquals(5, status.behindCount)
    }

    @Test
    fun parseStatus_modifiedStagedFile_appearsInStagedFiles() {
        // Arrange — 'M' in index column, ' ' in worktree
        val output = "## main\nM  src/Foo.kt\n"

        // Act
        val status = GitOutputParser.parseStatus(output)

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
        val status = GitOutputParser.parseStatus(output)

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
        val status = GitOutputParser.parseStatus(output)

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
        val status = GitOutputParser.parseStatus(output)

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
        val status = GitOutputParser.parseStatus(output)

        // Assert
        assertEquals(1, status.stagedFiles.size)
        assertEquals(1, status.unstagedFiles.size)
        assertEquals("src/Mixed.kt", status.stagedFiles[0].path)
        assertEquals("src/Mixed.kt", status.unstagedFiles[0].path)
    }

    @Test
    fun parseStatus_emptyOutput_returnsEmptyStatus() {
        // Arrange + Act
        val status = GitOutputParser.parseStatus("")

        // Assert
        assertEquals("", status.branch)
        assertTrue(status.isClean)
    }

    // ── parseFileStatus ───────────────────────────────────────────────────────

    @Test
    fun parseFileStatus_knownCharacters_returnCorrectStatuses() {
        assertEquals(GitFileStatus.MODIFIED, GitOutputParser.parseFileStatus('M'))
        assertEquals(GitFileStatus.ADDED, GitOutputParser.parseFileStatus('A'))
        assertEquals(GitFileStatus.DELETED, GitOutputParser.parseFileStatus('D'))
        assertEquals(GitFileStatus.RENAMED, GitOutputParser.parseFileStatus('R'))
        assertEquals(GitFileStatus.COPIED, GitOutputParser.parseFileStatus('C'))
    }

    @Test
    fun parseFileStatus_unknownCharacter_returnsNull() {
        assertNull(GitOutputParser.parseFileStatus('X'))
        assertNull(GitOutputParser.parseFileStatus('?'))
        assertNull(GitOutputParser.parseFileStatus(' '))
    }

    // ── parseDiff ─────────────────────────────────────────────────────────────

    @Test
    fun parseDiff_emptyOutput_returnsEmptyList() {
        // Arrange + Act
        val hunks = GitOutputParser.parseDiff("")

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
        val hunks = GitOutputParser.parseDiff(output)

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
        val hunks = GitOutputParser.parseDiff(output)
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
        val hunks = GitOutputParser.parseDiff(output)

        // Assert
        assertEquals(2, hunks.size)
    }

    @Test
    fun parseDiff_additionLine_hasCorrectNewLineNumber() {
        // Arrange — hunk starts at new line 5
        val output = "@@ -5,1 +5,2 @@\n+new line\n"

        // Act
        val hunk = GitOutputParser.parseDiff(output)[0]
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
        val hunk = GitOutputParser.parseDiff(output)[0]
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
        val line = GitOutputParser.parseDiff(output)[0].lines.first()

        // Assert — the leading '+' is stripped from content
        assertEquals("hello world", line.content)
    }

    // ── parseNumstat ──────────────────────────────────────────────────────────

    @Test
    fun parseNumstat_typicalOutput_returnsParsedStats() {
        // Arrange
        val output = "5\t3\tsrc/Main.kt\n10\t0\tsrc/New.kt\n"

        // Act
        val stats = GitOutputParser.parseNumstat(output)

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
        val stats = GitOutputParser.parseNumstat(output)

        // Assert
        assertNull(stats["binary.png"])
        assertNotNull(stats["text.kt"])
    }

    @Test
    fun parseNumstat_emptyOutput_returnsEmpty() {
        assertEquals(emptyMap(), GitOutputParser.parseNumstat(""))
    }

    // ── validateBranchName ────────────────────────────────────────────────────

    @Test
    fun validateBranchName_validName_doesNotThrow() {
        // Arrange + Act + Assert — no exception
        GitBranchParser.validateBranchName("main")
        GitBranchParser.validateBranchName("feature/my-feature")
        GitBranchParser.validateBranchName("release-1.0")
        GitBranchParser.validateBranchName("v2.0.0")
    }

    @Test
    fun validateBranchName_emptyName_throws() {
        assertFailsWith<GitServiceError.CommandFailed> {
            GitBranchParser.validateBranchName("")
        }
    }

    @Test
    fun validateBranchName_leadingDash_throws() {
        assertFailsWith<GitServiceError.CommandFailed> {
            GitBranchParser.validateBranchName("-bad-name")
        }
    }

    @Test
    fun validateBranchName_doubleDots_throws() {
        assertFailsWith<GitServiceError.CommandFailed> {
            GitBranchParser.validateBranchName("branch..name")
        }
    }

    @Test
    fun validateBranchName_containsSpace_throws() {
        assertFailsWith<GitServiceError.CommandFailed> {
            GitBranchParser.validateBranchName("branch name")
        }
    }

    @Test
    fun validateBranchName_containsTilde_throws() {
        assertFailsWith<GitServiceError.CommandFailed> {
            GitBranchParser.validateBranchName("branch~1")
        }
    }

    @Test
    fun validateBranchName_containsCaret_throws() {
        assertFailsWith<GitServiceError.CommandFailed> {
            GitBranchParser.validateBranchName("branch^HEAD")
        }
    }

    @Test
    fun validateBranchName_containsColon_throws() {
        assertFailsWith<GitServiceError.CommandFailed> {
            GitBranchParser.validateBranchName("origin:main")
        }
    }

    @Test
    fun validateBranchName_shellInjectionAttempt_throws() {
        assertFailsWith<GitServiceError.CommandFailed> {
            GitBranchParser.validateBranchName("main; rm -rf /")
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

    // ── P1: parseNumstat renamed file (R100) ──────────────────────────────────

    @Test
    fun parseNumstat_renamedFile_R100PercentSimilarity_parsedByNewPath() {
        // Arrange — git numstat for a rename: "R100" similarity shows as "<tab><tab>old => new"
        // The tab-separated format for renames is:
        //   <added>\t<deleted>\told-name => new-name
        // In practice git --numstat emits the destination path in the third column for renames.
        val output = "3\t1\tsrc/Old.kt => src/New.kt\n"

        // Act
        val stats = GitOutputParser.parseNumstat(output)

        // Assert — the entry is keyed on the full third-column value as-is.
        // The parser does not split rename arrows — that is intentional (callers use diff paths).
        assertEquals(1, stats.size)
        val stat = stats["src/Old.kt => src/New.kt"]
        assertNotNull(stat)
        assertEquals(GitDiffStat(added = 3, deleted = 1), stat)
    }

    @Test
    fun parseNumstat_pathWithSpaces_parsedCorrectly() {
        // Arrange — file names containing spaces must be preserved verbatim
        val output = "7\t2\tsrc/my feature/Main.kt\n"

        // Act
        val stats = GitOutputParser.parseNumstat(output)

        // Assert
        assertNotNull(stats["src/my feature/Main.kt"])
        assertEquals(GitDiffStat(added = 7, deleted = 2), stats["src/my feature/Main.kt"])
    }

    // ── P1: parseDiff "\ No newline at end of file" marker ───────────────────

    @Test
    fun parseDiff_noNewlineAtEndOfFileMarker_treatedAsContextLine() {
        // Arrange — git outputs this marker after a line that has no trailing newline
        val output = buildString {
            appendLine("@@ -1,1 +1,1 @@")
            appendLine("-old line")
            appendLine("\\ No newline at end of file")
            appendLine("+new line")
        }

        // Act — must not throw; marker line is treated as a context-like line
        val hunks = GitOutputParser.parseDiff(output)

        // Assert — the hunk is parsed without crashing
        assertEquals(1, hunks.size)
        // The "\" marker line must not be classified as DELETION or ADDITION
        val markerLine = hunks[0].lines.firstOrNull { it.content.contains("No newline") }
        if (markerLine != null) {
            assertEquals(DiffLineType.CONTEXT, markerLine.type)
        }
        // The real lines must still be present
        assertTrue(hunks[0].lines.any { it.type == DiffLineType.DELETION })
        assertTrue(hunks[0].lines.any { it.type == DiffLineType.ADDITION })
    }

    // ── P1: parseDiff conflict markers ───────────────────────────────────────

    @Test
    fun parseDiff_conflictMarkerLines_parsedWithoutCrashing() {
        // Arrange — diff output containing git conflict markers in context lines
        val output = buildString {
            appendLine("@@ -1,7 +1,7 @@")
            appendLine(" normal context")
            appendLine("+<<<<<<< HEAD")
            appendLine("+our change")
            appendLine("+=======")
            appendLine("+their change")
            appendLine("+>>>>>>> feature-branch")
            appendLine("-old line")
        }

        // Act — conflict markers must not crash the parser
        val hunks = GitOutputParser.parseDiff(output)

        // Assert
        assertEquals(1, hunks.size)
        // Lines starting with '+' are ADDITION — conflict markers inside additions are valid
        val additions = hunks[0].lines.filter { it.type == DiffLineType.ADDITION }
        assertTrue(additions.isNotEmpty(), "Conflict marker additions must be parsed")
        // The <<<<<<< line content should be stripped of the leading '+'
        assertTrue(additions.any { it.content.startsWith("<<<<<<<") })
        assertTrue(additions.any { it.content == "=======" })
        assertTrue(additions.any { it.content.startsWith(">>>>>>>") })
    }

    // ── P1: validateBranchName additional invalid cases ───────────────────────

    @Test
    fun validateBranchName_atBraceSequence_throws() {
        // @{ is forbidden by git ref rules (refspec expression)
        assertFailsWith<GitServiceError.CommandFailed> {
            GitBranchParser.validateBranchName("branch@{0}")
        }
    }

    @Test
    fun validateBranchName_trailingDotLock_doesNotThrow() {
        // NOTE: The current implementation does not reject ".lock" suffix names.
        // Git itself rejects refs ending with ".lock" (they conflict with lockfiles).
        // This test documents the actual behaviour — no exception is thrown.
        // If the validator is strengthened in future, update this to assertFailsWith.
        GitBranchParser.validateBranchName("feature.lock") // must not throw
    }

    @Test
    fun validateBranchName_leadingSlash_doesNotThrow() {
        // NOTE: The current regex allows '/' at any position including leading.
        // Git actually rejects leading-slash refs. Documents current (permissive) behaviour.
        GitBranchParser.validateBranchName("/bad-branch") // must not throw
    }

    @Test
    fun validateBranchName_doubleSlash_doesNotThrow() {
        // NOTE: The current regex allows consecutive slashes.
        // Git rejects "//". Documents current (permissive) behaviour.
        GitBranchParser.validateBranchName("feature//bad") // must not throw
    }

    @Test
    fun validateBranchName_singleDotName_doesNotThrow() {
        // NOTE: "." is matched by the regex and passes all explicit checks.
        // Git rejects "." as a branch name. Documents current (permissive) behaviour.
        GitBranchParser.validateBranchName(".") // must not throw
    }

    @Test
    fun validateBranchName_doubleDotsName_throws() {
        // ".." is caught by the explicit !name.contains("..") check.
        assertFailsWith<GitServiceError.CommandFailed> {
            GitBranchParser.validateBranchName("..")
        }
    }
}

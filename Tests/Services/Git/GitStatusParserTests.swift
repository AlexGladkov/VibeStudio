import XCTest
@testable import VibeStudio

final class GitStatusParserTests: XCTestCase {

    private var sut: GitService!

    override func setUp() {
        super.setUp()
        sut = GitService()
    }

    override func tearDown() {
        sut = nil
        super.tearDown()
    }

    // MARK: - Branch Parsing

    func testParseBranchName() async {
        let output = "## main\n"
        let status = await sut.parseStatus(output)

        XCTAssertEqual(status.branch, "main")
        XCTAssertEqual(status.aheadCount, 0)
        XCTAssertEqual(status.behindCount, 0)
    }

    func testParseBranchWithAheadBehind() async {
        let output = "## main...origin/main [ahead 2, behind 1]\n"
        let status = await sut.parseStatus(output)

        XCTAssertEqual(status.branch, "main")
        XCTAssertEqual(status.aheadCount, 2)
        XCTAssertEqual(status.behindCount, 1)
    }

    func testParseBranchWithAheadOnly() async {
        let output = "## feature/xyz...origin/feature/xyz [ahead 5]\n"
        let status = await sut.parseStatus(output)

        XCTAssertEqual(status.branch, "feature/xyz")
        XCTAssertEqual(status.aheadCount, 5)
        XCTAssertEqual(status.behindCount, 0)
    }

    func testParseBranchWithBehindOnly() async {
        let output = "## develop...origin/develop [behind 3]\n"
        let status = await sut.parseStatus(output)

        XCTAssertEqual(status.branch, "develop")
        XCTAssertEqual(status.aheadCount, 0)
        XCTAssertEqual(status.behindCount, 3)
    }

    func testParseDetachedHead() async {
        // Detached HEAD in porcelain v1: "## HEAD (no branch)"
        let output = "## HEAD (no branch)\n"
        let status = await sut.parseStatus(output)

        // parseStatus extracts text before space: "HEAD"
        XCTAssertEqual(status.branch, "HEAD")
        XCTAssertEqual(status.aheadCount, 0)
        XCTAssertEqual(status.behindCount, 0)
    }

    func testParseBranchNoRemoteTracking() async {
        // Local-only branch without remote tracking info
        let output = "## my-local-branch\n"
        let status = await sut.parseStatus(output)

        XCTAssertEqual(status.branch, "my-local-branch")
        XCTAssertEqual(status.aheadCount, 0)
        XCTAssertEqual(status.behindCount, 0)
    }

    // MARK: - Staged Files

    func testParseStagedModified() async {
        let output = "## main\nM  Sources/foo.swift\n"
        let status = await sut.parseStatus(output)

        XCTAssertEqual(status.stagedFiles.count, 1)
        XCTAssertEqual(status.stagedFiles.first?.path, "Sources/foo.swift")
        XCTAssertEqual(status.stagedFiles.first?.status, .modified)
        XCTAssertTrue(status.unstagedFiles.isEmpty)
        XCTAssertTrue(status.untrackedFiles.isEmpty)
    }

    func testParseStagedAdded() async {
        let output = "## main\nA  NewFile.swift\n"
        let status = await sut.parseStatus(output)

        XCTAssertEqual(status.stagedFiles.count, 1)
        XCTAssertEqual(status.stagedFiles.first?.path, "NewFile.swift")
        XCTAssertEqual(status.stagedFiles.first?.status, .added)
    }

    func testParseStagedDeleted() async {
        let output = "## main\nD  OldFile.swift\n"
        let status = await sut.parseStatus(output)

        XCTAssertEqual(status.stagedFiles.count, 1)
        XCTAssertEqual(status.stagedFiles.first?.path, "OldFile.swift")
        XCTAssertEqual(status.stagedFiles.first?.status, .deleted)
    }

    func testParseStagedRenamed() async {
        let output = "## main\nR  old.swift -> new.swift\n"
        let status = await sut.parseStatus(output)

        XCTAssertEqual(status.stagedFiles.count, 1)
        XCTAssertEqual(status.stagedFiles.first?.status, .renamed)
        // Path includes the full "old.swift -> new.swift" portion
        XCTAssertEqual(status.stagedFiles.first?.path, "old.swift -> new.swift")
    }

    func testParseStagedCopied() async {
        let output = "## main\nC  source.swift -> copy.swift\n"
        let status = await sut.parseStatus(output)

        XCTAssertEqual(status.stagedFiles.count, 1)
        XCTAssertEqual(status.stagedFiles.first?.status, .copied)
    }

    // MARK: - Unstaged Files

    func testParseUnstagedModified() async {
        let output = "## main\n M Sources/foo.swift\n"
        let status = await sut.parseStatus(output)

        XCTAssertTrue(status.stagedFiles.isEmpty)
        XCTAssertEqual(status.unstagedFiles.count, 1)
        XCTAssertEqual(status.unstagedFiles.first?.path, "Sources/foo.swift")
        XCTAssertEqual(status.unstagedFiles.first?.status, .modified)
    }

    func testParseUnstagedDeleted() async {
        let output = "## main\n D Removed.swift\n"
        let status = await sut.parseStatus(output)

        XCTAssertEqual(status.unstagedFiles.count, 1)
        XCTAssertEqual(status.unstagedFiles.first?.status, .deleted)
    }

    // MARK: - Untracked Files

    func testParseUntrackedFile() async {
        let output = "## main\n?? new-file.txt\n"
        let status = await sut.parseStatus(output)

        XCTAssertTrue(status.stagedFiles.isEmpty)
        XCTAssertTrue(status.unstagedFiles.isEmpty)
        XCTAssertEqual(status.untrackedFiles.count, 1)
        XCTAssertEqual(status.untrackedFiles.first?.path, "new-file.txt")
        XCTAssertEqual(status.untrackedFiles.first?.status, .untracked)
    }

    func testParseMultipleUntrackedFiles() async {
        let output = "## main\n?? file1.txt\n?? file2.txt\n?? dir/file3.txt\n"
        let status = await sut.parseStatus(output)

        XCTAssertEqual(status.untrackedFiles.count, 3)
        XCTAssertEqual(status.untrackedFiles[0].path, "file1.txt")
        XCTAssertEqual(status.untrackedFiles[1].path, "file2.txt")
        XCTAssertEqual(status.untrackedFiles[2].path, "dir/file3.txt")
    }

    // MARK: - Mixed Status

    func testParseMixedStagedAndUnstaged() async {
        // File modified in both index (staged) and worktree (unstaged)
        let output = "## main\nMM Sources/foo.swift\n"
        let status = await sut.parseStatus(output)

        XCTAssertEqual(status.stagedFiles.count, 1)
        XCTAssertEqual(status.stagedFiles.first?.status, .modified)
        XCTAssertEqual(status.unstagedFiles.count, 1)
        XCTAssertEqual(status.unstagedFiles.first?.status, .modified)
    }

    func testParseMultipleFileTypes() async {
        let output = """
        ## develop...origin/develop [ahead 1]
        M  Sources/modified.swift
         M Sources/worktree-changed.swift
        A  Sources/new-file.swift
        D  Sources/removed.swift
        ?? untracked.txt

        """
        let status = await sut.parseStatus(output)

        XCTAssertEqual(status.branch, "develop")
        XCTAssertEqual(status.aheadCount, 1)
        XCTAssertEqual(status.stagedFiles.count, 3) // M, A, D
        XCTAssertEqual(status.unstagedFiles.count, 1) // worktree M
        XCTAssertEqual(status.untrackedFiles.count, 1) // ??
    }

    // MARK: - Empty Output

    func testEmptyOutputOnlyBranch() async {
        let output = "## main\n"
        let status = await sut.parseStatus(output)

        XCTAssertEqual(status.branch, "main")
        XCTAssertTrue(status.stagedFiles.isEmpty)
        XCTAssertTrue(status.unstagedFiles.isEmpty)
        XCTAssertTrue(status.untrackedFiles.isEmpty)
        XCTAssertTrue(status.isClean)
    }

    func testCompletelyEmptyOutput() async {
        let output = ""
        let status = await sut.parseStatus(output)

        XCTAssertEqual(status.branch, "")
        XCTAssertTrue(status.stagedFiles.isEmpty)
        XCTAssertTrue(status.unstagedFiles.isEmpty)
        XCTAssertTrue(status.untrackedFiles.isEmpty)
    }

    // MARK: - parseFileStatus

    func testParseFileStatusCharacters() async {
        let m = await sut.parseFileStatus("M")
        XCTAssertEqual(m, .modified)

        let a = await sut.parseFileStatus("A")
        XCTAssertEqual(a, .added)

        let d = await sut.parseFileStatus("D")
        XCTAssertEqual(d, .deleted)

        let r = await sut.parseFileStatus("R")
        XCTAssertEqual(r, .renamed)

        let c = await sut.parseFileStatus("C")
        XCTAssertEqual(c, .copied)
    }

    func testParseFileStatusUnknownCharReturnsNil() async {
        let x = await sut.parseFileStatus("X")
        XCTAssertNil(x)

        let space = await sut.parseFileStatus(" ")
        XCTAssertNil(space)

        let question = await sut.parseFileStatus("?")
        XCTAssertNil(question)
    }

    // MARK: - parseDiff

    func testParseDiffSingleHunk() async {
        let diffOutput = """
        diff --git a/file.swift b/file.swift
        index 1234567..abcdefg 100644
        --- a/file.swift
        +++ b/file.swift
        @@ -10,3 +10,4 @@ func example() {
         context line
        -removed line
        +added line
        +another added line
        """
        let hunks = await sut.parseDiff(diffOutput)

        XCTAssertEqual(hunks.count, 1)
        XCTAssertTrue(hunks[0].header.hasPrefix("@@ -10,3 +10,4"))

        let lines = hunks[0].lines
        XCTAssertEqual(lines.count, 4)

        // Context line
        XCTAssertEqual(lines[0].type, .context)
        XCTAssertEqual(lines[0].content, "context line")
        XCTAssertEqual(lines[0].oldLineNumber, 10)
        XCTAssertEqual(lines[0].newLineNumber, 10)

        // Deletion
        XCTAssertEqual(lines[1].type, .deletion)
        XCTAssertEqual(lines[1].content, "removed line")
        XCTAssertEqual(lines[1].oldLineNumber, 11)
        XCTAssertNil(lines[1].newLineNumber)

        // Addition
        XCTAssertEqual(lines[2].type, .addition)
        XCTAssertEqual(lines[2].content, "added line")
        XCTAssertNil(lines[2].oldLineNumber)
        XCTAssertEqual(lines[2].newLineNumber, 11)

        // Second addition
        XCTAssertEqual(lines[3].type, .addition)
        XCTAssertEqual(lines[3].content, "another added line")
        XCTAssertNil(lines[3].oldLineNumber)
        XCTAssertEqual(lines[3].newLineNumber, 12)
    }

    func testParseDiffMultipleHunks() async {
        let diffOutput = """
        diff --git a/file.swift b/file.swift
        @@ -1,3 +1,3 @@
         line1
        -old2
        +new2
         line3
        @@ -20,2 +20,3 @@
         foo
        +bar
         baz
        """
        let hunks = await sut.parseDiff(diffOutput)

        XCTAssertEqual(hunks.count, 2)
        XCTAssertTrue(hunks[0].header.contains("-1,3 +1,3"))
        XCTAssertTrue(hunks[1].header.contains("-20,2 +20,3"))
    }

    func testParseDiffEmptyOutput() async {
        let hunks = await sut.parseDiff("")
        XCTAssertTrue(hunks.isEmpty)
    }

    func testParseDiffNoHunks() async {
        let diffOutput = """
        diff --git a/file.swift b/file.swift
        index 1234567..abcdefg 100644
        --- a/file.swift
        +++ b/file.swift
        """
        let hunks = await sut.parseDiff(diffOutput)
        XCTAssertTrue(hunks.isEmpty)
    }
}

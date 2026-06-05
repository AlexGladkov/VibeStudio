// MARK: - GitServiceTests
// Covers stage/unstage/commit/push/pull/fetch/checkout/createBranch happy &
// error paths via the MockProcessSpawning seam.
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

final class GitServiceTests: XCTestCase {

    private var spawner: MockProcessSpawning!
    private var sut: GitService!
    private let repo = URL(fileURLWithPath: "/tmp/fake-repo")

    override func setUp() {
        super.setUp()
        spawner = MockProcessSpawning()
        sut = GitService(spawner: spawner)
    }

    override func tearDown() {
        sut = nil
        spawner = nil
        super.tearDown()
    }

    // MARK: - Helpers

    private func ok(_ stdout: String = "", _ stderr: String = "") -> ProcessResult {
        ProcessResult(exitCode: 0, stdout: stdout, stderr: stderr)
    }

    private func fail(_ code: Int32, _ stderr: String) -> ProcessResult {
        ProcessResult(exitCode: code, stdout: "", stderr: stderr)
    }

    // MARK: - stage / unstage

    func test_stage_emptyFiles_invokesAddAllSwitch() async throws {
        spawner.enqueue(ok())
        try await sut.stage(files: [], at: repo)
        XCTAssertEqual(spawner.lastGitArgs, ["add", "-A"])
    }

    func test_stage_files_passesPathsAfterDoubleDash() async throws {
        spawner.enqueue(ok())
        try await sut.stage(files: ["a.txt", "b/c.swift"], at: repo)
        XCTAssertEqual(spawner.lastGitArgs, ["add", "--", "a.txt", "b/c.swift"])
    }

    func test_unstage_emptyFiles_restoresAllStaged() async throws {
        spawner.enqueue(ok())
        try await sut.unstage(files: [], at: repo)
        XCTAssertEqual(spawner.lastGitArgs, ["restore", "--staged", "."])
    }

    func test_unstage_singleFile_includesDoubleDash() async throws {
        spawner.enqueue(ok())
        try await sut.unstage(files: ["x.swift"], at: repo)
        XCTAssertEqual(spawner.lastGitArgs, ["restore", "--staged", "--", "x.swift"])
    }

    // MARK: - commit

    func test_commit_returnsExtractedHash() async throws {
        spawner.enqueue(ok("[main abc1234] subject line\n 1 file changed"))
        let hash = try await sut.commit(message: "feat: stuff", at: repo)
        XCTAssertEqual(hash, "abc1234")
        XCTAssertEqual(spawner.lastGitArgs, ["commit", "-m", "feat: stuff"])
    }

    func test_commit_blankMessage_throwsWithoutInvokingGit() async {
        do {
            _ = try await sut.commit(message: "   \n\t", at: repo)
            XCTFail("expected commit to throw on empty message")
        } catch let GitServiceError.commandFailed(command, _, _) {
            XCTAssertEqual(command, "commit")
            XCTAssertEqual(spawner.callCount, 0, "git must not be invoked for blank messages")
        } catch {
            XCTFail("unexpected error: \(error)")
        }
    }

    // MARK: - push / pull / fetch

    func test_push_happyPath_callsGitPush() async throws {
        spawner.enqueue(ok())
        try await sut.push(remote: "origin", at: repo)
        XCTAssertEqual(spawner.lastGitArgs, ["push", "origin"])
    }

    func test_nonNetworkCommand_setsCredentialSuppressionEnv() async throws {
        // Sanity: read-only commands (status) actively set GIT_TERMINAL_PROMPT=0
        // so we don't fork an interactive prompt that hangs the test process.
        spawner.enqueue(ok("## main\n"))
        _ = try await sut.status(at: repo)
        let env = spawner.lastCall?.env ?? [:]
        XCTAssertEqual(env["GIT_TERMINAL_PROMPT"], "0")
        XCTAssertEqual(env["GIT_ASKPASS"], "/usr/bin/true")
    }

    func test_push_rejected_mapsToPushRejectedError() async {
        spawner.enqueue(fail(1, "! [rejected]        main -> main (fetch first)"))
        do {
            try await sut.push(remote: "origin", at: repo)
            XCTFail("expected pushRejected")
        } catch let GitServiceError.pushRejected(reason) {
            XCTAssertTrue(reason.contains("rejected"))
        } catch {
            XCTFail("unexpected error: \(error)")
        }
    }

    func test_pull_conflict_mapsToMergeConflictError() async {
        let stderr = """
        CONFLICT (content): Merge conflict in foo.swift
        CONFLICT (content): Merge conflict in bar.swift
        """
        spawner.enqueue(fail(1, stderr))
        do {
            try await sut.pull(remote: "origin", at: repo)
            XCTFail("expected mergeConflict")
        } catch let GitServiceError.mergeConflict(files) {
            XCTAssertEqual(files.count, 2)
        } catch {
            XCTFail("unexpected error: \(error)")
        }
    }

    func test_fetch_invalidRemoteName_throwsBeforeGitInvoked() async {
        do {
            try await sut.fetch(remote: "-evil", at: repo)
            XCTFail("expected validateBranchName to reject leading dash")
        } catch {
            XCTAssertEqual(spawner.callCount, 0, "validation must short-circuit")
        }
    }

    // MARK: - checkout / createBranch

    func test_checkout_usesSwitch() async throws {
        spawner.enqueue(ok())
        try await sut.checkout(branch: "main", at: repo)
        XCTAssertEqual(spawner.lastGitArgs, ["switch", "main"])
    }

    func test_createBranch_withStartPoint_appendsStartPoint() async throws {
        spawner.enqueue(ok())
        try await sut.createBranch(name: "feature/x", from: "main", at: repo)
        XCTAssertEqual(spawner.lastGitArgs, ["switch", "-c", "feature/x", "main"])
    }

    func test_createBranch_invalidName_rejected() async {
        do {
            try await sut.createBranch(name: "..bad", from: nil, at: repo)
            XCTFail("expected rejection")
        } catch {
            XCTAssertEqual(spawner.callCount, 0)
        }
    }

    // MARK: - notARepository mapping

    func test_status_inNonRepository_mapsToNotARepository() async {
        spawner.enqueue(fail(128, "fatal: not a git repository (or any of the parent directories): .git"))
        do {
            _ = try await sut.status(at: repo)
            XCTFail("expected notARepository")
        } catch let GitServiceError.notARepository(path) {
            XCTAssertEqual(path, repo)
        } catch {
            XCTFail("unexpected: \(error)")
        }
    }

    // MARK: - isRepository / remoteURL helpers

    func test_isRepository_truePath() async {
        spawner.enqueue(ok("true\n"))
        let result = await sut.isRepository(at: repo)
        XCTAssertTrue(result)
        XCTAssertEqual(spawner.lastGitArgs, ["rev-parse", "--is-inside-work-tree"])
    }

    func test_isRepository_falsePath() async {
        spawner.enqueue(fail(128, "fatal: not a git repository"))
        let result = await sut.isRepository(at: repo)
        XCTAssertFalse(result)
    }

    func test_remoteURL_returnsTrimmedValue() async {
        spawner.enqueue(ok("https://github.com/user/repo.git\n"))
        let url = await sut.remoteURL(name: "origin", at: repo)
        XCTAssertEqual(url, "https://github.com/user/repo.git")
    }

    func test_remoteURL_failureReturnsNil() async {
        spawner.enqueue(fail(2, "No such remote"))
        let url = await sut.remoteURL(name: "origin", at: repo)
        XCTAssertNil(url)
    }

    // MARK: - addRemote

    func test_addRemote_dangerousScheme_rejected() async {
        do {
            try await sut.addRemote(name: "origin", url: "ext::sh -c 'rm -rf /'", at: repo)
            XCTFail("expected rejection of ext:: scheme")
        } catch {
            XCTAssertEqual(spawner.callCount, 0)
        }
    }

    func test_addRemote_validURL_invokesGitRemoteAdd() async throws {
        spawner.enqueue(ok())
        try await sut.addRemote(name: "origin", url: "https://github.com/u/r.git", at: repo)
        XCTAssertEqual(spawner.lastGitArgs, ["remote", "add", "origin", "https://github.com/u/r.git"])
    }

    // MARK: - aheadBehind parsing

    func test_aheadBehind_parsesTabSeparatedCounts() async throws {
        spawner.enqueue(ok("3\t5\n"))
        let (ahead, behind) = try await sut.aheadBehind(at: repo)
        XCTAssertEqual(ahead, 3)
        XCTAssertEqual(behind, 5)
    }

    func test_aheadBehind_malformedOutput_returnsZeros() async throws {
        spawner.enqueue(ok("garbage"))
        let (ahead, behind) = try await sut.aheadBehind(at: repo)
        XCTAssertEqual(ahead, 0)
        XCTAssertEqual(behind, 0)
    }

    // MARK: - log

    func test_log_parsesCommits() async throws {
        let output = """
        abc1234567890\tabc1234\tFirst commit\tAlice\t2024-01-02T03:04:05Z
        def4567890abc\tdef4567\tSecond\tBob\t2024-02-01T00:00:00Z
        """
        spawner.enqueue(ok(output))
        let commits = try await sut.log(limit: 10, at: repo)
        XCTAssertEqual(commits.count, 2)
        XCTAssertEqual(commits[0].shortHash, "abc1234")
        XCTAssertEqual(commits[1].author, "Bob")
    }

    // MARK: - cwd discipline — git uses `-C <dir>`, NOT currentDirectoryURL

    func test_runGit_passesDirViaCArg_notCWD() async throws {
        spawner.enqueue(ok())
        try await sut.checkout(branch: "main", at: repo)
        let call = try XCTUnwrap(spawner.lastCall)
        XCTAssertNil(call.cwd, "GitService must use `-C <path>` rather than setting cwd (TCC)")
        XCTAssertEqual(call.args.prefix(2), ["-C", repo.path].prefix(2))
    }

    // MARK: - timeout mapping

    func test_runGit_timeout_mapsToGitTimeoutError() async {
        spawner.enqueueError(ProcessSpawnError.timedOut(seconds: 30))
        do {
            _ = try await sut.status(at: repo)
            XCTFail("expected timeout error")
        } catch let GitServiceError.timeout(command, seconds) {
            XCTAssertEqual(command, "status")
            XCTAssertEqual(seconds, 30)
        } catch {
            XCTFail("unexpected: \(error)")
        }
    }
}

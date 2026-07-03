import XCTest
@testable import VibeStudio

/// Unit tests for ``FileDiffViewModel`` presentation logic: binary short-circuit,
/// missing-project guard, oversized-diff truncation, and error propagation.
/// Uses ``MockGitService`` so no real git process is spawned.
@MainActor
final class FileDiffViewModelTests: XCTestCase {

    private let repo = URL(fileURLWithPath: "/tmp/repo")

    // MARK: - Fixtures

    private struct StubError: LocalizedError {
        var errorDescription: String? { "stub diff failure" }
    }

    /// Build a hunk whose single line carries `byteCount` UTF-8 bytes.
    private func makeLargeHunk(header: String, byteCount: Int) -> GitDiffHunk {
        let content = String(repeating: "a", count: byteCount)
        let line = GitDiffLine(type: .addition, content: content, oldLineNumber: nil, newLineNumber: 1)
        return GitDiffHunk(header: header, lines: [line])
    }

    // MARK: - Binary Short-Circuit

    func testBinaryFileSkipsGitAndReportsBinary() async {
        let mock = MockGitService()
        let sut = FileDiffViewModel(gitService: mock)

        await sut.load(
            file: GitFile(path: "assets/image.png", status: .modified),
            staged: false,
            projectPath: repo
        )

        XCTAssertEqual(sut.errorMessage, "Binary file — diff not available")
        XCTAssertTrue(sut.hunks.isEmpty)
        XCTAssertFalse(sut.isLoading)
        let diffCalls = await mock.diffCallCount
        XCTAssertEqual(diffCalls, 0, "git diff must not be invoked for binary files")
    }

    // MARK: - Missing Project

    func testNilProjectPathReportsNoActiveProject() async {
        let mock = MockGitService()
        let sut = FileDiffViewModel(gitService: mock)

        await sut.load(
            file: GitFile(path: "Sources/foo.swift", status: .modified),
            staged: false,
            projectPath: nil
        )

        XCTAssertEqual(sut.errorMessage, "No active project")
        XCTAssertTrue(sut.hunks.isEmpty)
        XCTAssertFalse(sut.isLoading)
        let diffCalls = await mock.diffCallCount
        XCTAssertEqual(diffCalls, 0)
    }

    // MARK: - Oversized Diff Truncation

    func testOversizedDiffIsTruncatedWithWarning() async {
        let mock = MockGitService()
        // Three ~250KB hunks (750KB total) exceed the 512KB cap.
        let loaded = [
            makeLargeHunk(header: "@@ -1 +1 @@", byteCount: 250_000),
            makeLargeHunk(header: "@@ -2 +2 @@", byteCount: 250_000),
            makeLargeHunk(header: "@@ -3 +3 @@", byteCount: 250_000)
        ]
        await mock.setDiffResult(.success(loaded))

        let sut = FileDiffViewModel(gitService: mock)
        await sut.load(
            file: GitFile(path: "Sources/big.swift", status: .modified),
            staged: false,
            projectPath: repo
        )

        XCTAssertNotNil(sut.sizeWarning, "oversized diff should set a size warning")
        XCTAssertLessThan(sut.hunks.count, loaded.count, "hunks should be truncated")
        XCTAssertFalse(sut.hunks.isEmpty, "at least the first hunk fits under the cap")
        XCTAssertNil(sut.errorMessage)
        XCTAssertFalse(sut.isLoading)
    }

    func testSmallDiffKeepsAllHunksWithoutWarning() async {
        let mock = MockGitService()
        let loaded = [makeLargeHunk(header: "@@ -1 +1 @@", byteCount: 100)]
        await mock.setDiffResult(.success(loaded))

        let sut = FileDiffViewModel(gitService: mock)
        await sut.load(
            file: GitFile(path: "Sources/small.swift", status: .modified),
            staged: false,
            projectPath: repo
        )

        XCTAssertNil(sut.sizeWarning)
        XCTAssertEqual(sut.hunks.count, 1)
        XCTAssertNil(sut.errorMessage)
        XCTAssertFalse(sut.isLoading)
    }

    // MARK: - Error Propagation

    func testGitErrorPopulatesErrorMessage() async {
        let mock = MockGitService()
        await mock.setDiffResult(.failure(StubError()))

        let sut = FileDiffViewModel(gitService: mock)
        await sut.load(
            file: GitFile(path: "Sources/foo.swift", status: .modified),
            staged: false,
            projectPath: repo
        )

        XCTAssertEqual(sut.errorMessage, "stub diff failure")
        XCTAssertTrue(sut.hunks.isEmpty)
        XCTAssertNil(sut.sizeWarning)
        XCTAssertFalse(sut.isLoading)
    }
}

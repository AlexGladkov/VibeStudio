// MARK: - FileTreeViewModelTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

@MainActor
final class FileTreeViewModelTests: XCTestCase {

    func test_toggleDirectory_addsThenRemoves() {
        let sut = FileTreeViewModel()
        XCTAssertTrue(sut.expandedDirs.isEmpty)

        sut.toggleDirectory("/a/b")
        XCTAssertTrue(sut.expandedDirs.contains("/a/b"))

        sut.toggleDirectory("/a/b")
        XCTAssertFalse(sut.expandedDirs.contains("/a/b"))
    }

    func test_expandDirectory_idempotent() {
        let sut = FileTreeViewModel()
        sut.expandDirectory("/x")
        sut.expandDirectory("/x")
        XCTAssertEqual(sut.expandedDirs.count, 1)
    }

    func test_rebuildTree_onMissingPath_resultsInEmpty() async throws {
        let sut = FileTreeViewModel()
        let missing = URL(fileURLWithPath: "/this/path/does/not/exist/\(UUID().uuidString)")
        sut.rebuildTree(at: missing)
        try await Task.sleep(for: .milliseconds(150))
        XCTAssertTrue(sut.tree.isEmpty)
    }

    func test_rebuildTree_onValidDirectory_populatesNodes() async throws {
        let tmp = FileManager.default.temporaryDirectory
            .appendingPathComponent("vs-tree-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: tmp, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tmp) }
        try Data("hello".utf8).write(to: tmp.appendingPathComponent("file.txt"))

        let sut = FileTreeViewModel()
        sut.rebuildTree(at: tmp)
        try await Task.sleep(for: .milliseconds(200))
        XCTAssertFalse(sut.tree.isEmpty)
    }

    func test_cancelRebuild_doesNotCrash() {
        let sut = FileTreeViewModel()
        sut.rebuildTree(at: FileManager.default.temporaryDirectory)
        sut.cancelRebuild()
        // Just verifying no crash; tree state may be empty or populated depending on timing.
    }
}

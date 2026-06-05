// MARK: - FileSystemWatcherTests
// Validates watch/unwatch lifecycle, error paths, and that deinit releases
// every FSEventStream without leaking.

import XCTest
@testable import VibeStudio

final class FileSystemWatcherTests: XCTestCase {

    private var tmpDir: URL!

    override func setUpWithError() throws {
        try super.setUpWithError()
        tmpDir = FileManager.default.temporaryDirectory.appending(path: "fsw-tests-\(UUID().uuidString)", directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: tmpDir, withIntermediateDirectories: true)
    }

    override func tearDownWithError() throws {
        if let tmpDir { try? FileManager.default.removeItem(at: tmpDir) }
        try super.tearDownWithError()
    }

    // MARK: - watch happy path

    func test_watch_directory_returnsToken_andRegistersActiveWatch() throws {
        let watcher = FileSystemWatcher()
        defer { watcher.unwatchAll() }

        let token = try watcher.watch(directory: tmpDir)
        XCTAssertEqual(watcher.activeWatches.count, 1)
        XCTAssertEqual(watcher.activeWatches.first?.token, token)
        XCTAssertEqual(watcher.activeWatches.first?.directory, tmpDir)
    }

    // MARK: - error paths

    func test_watch_pathNotFound_throws() {
        let watcher = FileSystemWatcher()
        let missing = tmpDir.appending(path: "does-not-exist", directoryHint: .isDirectory)
        XCTAssertThrowsError(try watcher.watch(directory: missing)) { error in
            guard case FileSystemWatcherError.pathNotFound = error else {
                return XCTFail("expected .pathNotFound, got \(error)")
            }
        }
    }

    func test_watch_forbiddenRoot_throws() {
        let watcher = FileSystemWatcher()
        // "/" is in PathConstants.forbiddenRootPaths.
        XCTAssertThrowsError(try watcher.watch(directory: URL(fileURLWithPath: "/")))
    }

    func test_watch_sameDirectoryTwice_throwsAlreadyWatching() throws {
        let watcher = FileSystemWatcher()
        defer { watcher.unwatchAll() }
        _ = try watcher.watch(directory: tmpDir)
        XCTAssertThrowsError(try watcher.watch(directory: tmpDir)) { error in
            guard case FileSystemWatcherError.alreadyWatching = error else {
                return XCTFail("expected .alreadyWatching, got \(error)")
            }
        }
    }

    // MARK: - unwatch

    func test_unwatch_removesActiveWatch() throws {
        let watcher = FileSystemWatcher()
        let token = try watcher.watch(directory: tmpDir)
        XCTAssertEqual(watcher.activeWatches.count, 1)

        watcher.unwatch(token)

        // unwatch is async barrier — poll briefly.
        for _ in 0..<20 where !watcher.activeWatches.isEmpty {
            sleepMS(50)
        }
        XCTAssertTrue(watcher.activeWatches.isEmpty)
    }

    func test_unwatchAll_removesAllWatches_andCanBeCalledRepeatedly() throws {
        let watcher = FileSystemWatcher()
        let dir2 = tmpDir.appending(path: "sub", directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: dir2, withIntermediateDirectories: true)

        _ = try watcher.watch(directory: tmpDir)
        _ = try watcher.watch(directory: dir2)
        XCTAssertEqual(watcher.activeWatches.count, 2)

        watcher.unwatchAll()
        // Idempotent — second call must not crash.
        watcher.unwatchAll()

        for _ in 0..<20 where !watcher.activeWatches.isEmpty {
            sleepMS(50)
        }
        XCTAssertTrue(watcher.activeWatches.isEmpty)
    }

    // MARK: - deinit cleanup

    func test_deinit_releasesStreams_withoutLeakingActiveWatches() throws {
        weak var weakRef: FileSystemWatcher?
        try autoreleasepool {
            let watcher = FileSystemWatcher()
            weakRef = watcher
            _ = try watcher.watch(directory: tmpDir)
        }
        // Without leaks, FileSystemWatcher should be released; deinit must run
        // its sync barrier cleanup. If it deadlocked we'd hang the test.
        XCTAssertNil(weakRef, "FileSystemWatcher leaked — likely missing FSEventStream release in deinit")
    }

    // MARK: - Helpers

    private func sleepMS(_ ms: Int) {
        usleep(useconds_t(ms * 1000))
    }
}

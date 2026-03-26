// MARK: - SessionStoreTests
// Unit tests for SessionStore (Phase 2).
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

final class SessionStoreTests: XCTestCase {

    // MARK: - Properties

    private var tempDir: URL!
    private var store: SessionStore!

    // MARK: - Lifecycle

    override func setUp() async throws {
        try await super.setUp()
        tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("SessionStoreTests-\(UUID().uuidString)")
        try FileManager.default.createDirectory(
            at: tempDir,
            withIntermediateDirectories: true
        )
        store = SessionStore(storageDirectory: tempDir)
    }

    override func tearDown() async throws {
        store = nil
        try? FileManager.default.removeItem(at: tempDir)
        tempDir = nil
        try await super.tearDown()
    }

    // MARK: - Helpers

    /// Creates a minimal valid AppSessionSnapshot for testing.
    private func makeSnapshot(
        activeProjectId: UUID? = nil,
        projectSessions: [ProjectSessionSnapshot] = []
    ) -> AppSessionSnapshot {
        AppSessionSnapshot(
            version: 1,
            capturedAt: Date(),
            activeProjectId: activeProjectId,
            projectSessions: projectSessions
        )
    }

    // MARK: - Snapshot: save / restore round-trip

    func testSaveAndRestoreSnapshot() async throws {
        let projectId = UUID()
        let sessionId = UUID()

        let original = AppSessionSnapshot(
            version: 1,
            capturedAt: Date(),
            activeProjectId: projectId,
            projectSessions: [
                ProjectSessionSnapshot(
                    projectId: projectId,
                    terminalLayouts: [
                        TerminalLayoutSnapshot(
                            sessionId: sessionId,
                            title: "zsh",
                            splitDirection: nil,
                            workingDirectory: nil
                        )
                    ],
                    scrollbackFile: nil,
                    sidebarVisible: true,
                    sidebarWidth: 260.0
                )
            ]
        )

        try await store.save(snapshot: original)
        let restored = try await store.restore()

        XCTAssertNotNil(restored)
        XCTAssertEqual(restored?.activeProjectId, projectId)
        XCTAssertEqual(restored?.projectSessions.count, 1)
        XCTAssertEqual(restored?.projectSessions.first?.projectId, projectId)
        XCTAssertEqual(restored?.projectSessions.first?.terminalLayouts.count, 1)
        XCTAssertEqual(
            restored?.projectSessions.first?.terminalLayouts.first?.sessionId,
            sessionId
        )
        XCTAssertEqual(restored?.projectSessions.first?.sidebarVisible, true)
        XCTAssertEqual(
            restored?.projectSessions.first?.sidebarWidth ?? 0.0,
            260.0,
            accuracy: 0.01
        )
    }

    func testSaveOverwritesPreviousSnapshot() async throws {
        let first = makeSnapshot(activeProjectId: UUID())
        try await store.save(snapshot: first)

        let secondId = UUID()
        let second = makeSnapshot(activeProjectId: secondId)
        try await store.save(snapshot: second)

        let restored = try await store.restore()
        XCTAssertEqual(restored?.activeProjectId, secondId)
    }

    // MARK: - Snapshot: restore from empty store

    func testRestoreNonexistentReturnsNil() async throws {
        let emptyDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("EmptySessionStore-\(UUID().uuidString)")
        try FileManager.default.createDirectory(
            at: emptyDir,
            withIntermediateDirectories: true
        )
        defer { try? FileManager.default.removeItem(at: emptyDir) }

        let emptyStore = SessionStore(storageDirectory: emptyDir)
        let result = try await emptyStore.restore()

        XCTAssertNil(result, "Restore from empty directory should return nil, not throw")
    }

    // MARK: - Snapshot: clear

    func testClearRemovesSnapshot() async throws {
        let snapshot = makeSnapshot(activeProjectId: UUID())
        try await store.save(snapshot: snapshot)

        try await store.clear()

        let restored = try await store.restore()
        XCTAssertNil(restored, "After clear(), restore() should return nil")
    }

    // MARK: - Snapshot: version check

    func testIncompatibleVersionThrows() async throws {
        // Manually write a snapshot with version > currentSnapshotVersion.
        let futureSnapshot: [String: Any] = [
            "version": 999,
            "capturedAt": ISO8601DateFormatter().string(from: Date()),
            "projectSessions": [] as [Any]
        ]
        let data = try JSONSerialization.data(withJSONObject: futureSnapshot)
        let snapshotURL = tempDir.appendingPathComponent("session.json")
        try data.write(to: snapshotURL)

        do {
            _ = try await store.restore()
            XCTFail("Expected SessionPersistenceError.incompatibleVersion")
        } catch let error as SessionPersistenceError {
            guard case .incompatibleVersion(let found, let expected) = error else {
                return XCTFail(
                    "Expected incompatibleVersion, got \(error)"
                )
            }
            XCTAssertEqual(found, 999)
            XCTAssertEqual(expected, 1)
        }
    }

    // MARK: - Scrollback: CRUD

    func testScrollbackSaveAndLoad() async throws {
        let sessionId = UUID()
        let content = "Last login: Mon Mar 25 10:00:00\n$ echo hello\nhello\n$"

        try await store.saveScrollback(content, for: sessionId)
        let loaded = await store.loadScrollback(for: sessionId)

        XCTAssertEqual(loaded, content)
    }

    func testScrollbackLoadNonexistentReturnsNil() async {
        let loaded = await store.loadScrollback(for: UUID())
        XCTAssertNil(loaded)
    }

    func testScrollbackDelete() async throws {
        let sessionId = UUID()
        try await store.saveScrollback("some output", for: sessionId)

        try await store.deleteScrollback(for: sessionId)

        let loaded = await store.loadScrollback(for: sessionId)
        XCTAssertNil(loaded, "After deletion, loadScrollback should return nil")
    }

    func testScrollbackDeleteNonexistentDoesNotThrow() async throws {
        // Deleting a scrollback that was never saved should not throw.
        try await store.deleteScrollback(for: UUID())
    }

    func testScrollbackOverwrite() async throws {
        let sessionId = UUID()
        try await store.saveScrollback("first version", for: sessionId)
        try await store.saveScrollback("second version", for: sessionId)

        let loaded = await store.loadScrollback(for: sessionId)
        XCTAssertEqual(loaded, "second version")
    }

    // MARK: - Scrollback: prune orphaned

    func testPruneOrphanedScrollbacks() async throws {
        let activeId = UUID()
        let orphanA = UUID()
        let orphanB = UUID()

        try await store.saveScrollback("active session", for: activeId)
        try await store.saveScrollback("orphan A", for: orphanA)
        try await store.saveScrollback("orphan B", for: orphanB)

        let pruned = try await store.pruneOrphanedScrollbacks(
            keeping: Set([activeId])
        )

        XCTAssertEqual(pruned, 2, "Two orphaned scrollback files should be pruned")

        // Active session scrollback should survive.
        let activeContent = await store.loadScrollback(for: activeId)
        XCTAssertEqual(activeContent, "active session")

        // Orphans should be gone.
        let orphanAContent = await store.loadScrollback(for: orphanA)
        XCTAssertNil(orphanAContent)
        let orphanBContent = await store.loadScrollback(for: orphanB)
        XCTAssertNil(orphanBContent)
    }

    func testPruneWithNoOrphansReturnsZero() async throws {
        let id = UUID()
        try await store.saveScrollback("content", for: id)

        let pruned = try await store.pruneOrphanedScrollbacks(
            keeping: Set([id])
        )
        XCTAssertEqual(pruned, 0)
    }

    func testPruneEmptyDirectoryReturnsZero() async throws {
        let pruned = try await store.pruneOrphanedScrollbacks(
            keeping: Set<UUID>()
        )
        XCTAssertEqual(pruned, 0)
    }

    // MARK: - Scrollback: file permissions

    func testScrollbackFilePermissions() async throws {
        let sessionId = UUID()
        try await store.saveScrollback("sensitive data", for: sessionId)

        let scrollbackDir = tempDir.appendingPathComponent("scrollback")
        let fileURL = scrollbackDir.appendingPathComponent("\(sessionId.uuidString).txt")

        let attributes = try FileManager.default.attributesOfItem(
            atPath: fileURL.path
        )
        let permissions = attributes[.posixPermissions] as? Int

        // SessionStore sets 0o600 (owner read/write only).
        XCTAssertEqual(
            permissions,
            0o600,
            "Scrollback file should have restrictive 0600 permissions"
        )
    }

    // MARK: - currentSnapshotVersion

    func testCurrentSnapshotVersionIsOne() async {
        let version = await store.currentSnapshotVersion
        XCTAssertEqual(version, 1)
    }

    // MARK: - Snapshot with empty project sessions

    func testSnapshotWithEmptyProjectSessions() async throws {
        let snapshot = makeSnapshot(
            activeProjectId: nil,
            projectSessions: []
        )

        try await store.save(snapshot: snapshot)
        let restored = try await store.restore()

        XCTAssertNotNil(restored)
        XCTAssertNil(restored?.activeProjectId)
        XCTAssertEqual(restored?.projectSessions.count, 0)
    }
}

// MARK: - MockSessionPersistence
// In-memory mock of ``SessionPersisting`` for unit tests.
// macOS 14+, Swift 5.10

import Foundation
@testable import VibeStudio

/// Mock ``SessionPersisting`` that keeps state in memory.
///
/// Pre-configure ``restoreResult`` to control what `restore()` returns.
/// Inspect ``savedSnapshot`` and ``saveCallCount`` to verify interactions.
final class MockSessionPersistence: SessionPersisting, @unchecked Sendable {

    // MARK: - Configurable

    var restoreResult: Result<AppSessionSnapshot?, Error> = .success(nil)
    var saveErrorToThrow: Error?

    // MARK: - Recorded

    private(set) var saveCallCount = 0
    private(set) var savedSnapshot: AppSessionSnapshot?
    private(set) var restoreCallCount = 0
    private(set) var clearCallCount = 0

    // MARK: - Meta

    let storageDirectory: URL = URL(fileURLWithPath: "/tmp/mock-session")
    let currentSnapshotVersion: Int = 1

    // MARK: - Snapshot

    func save(snapshot: AppSessionSnapshot) async throws {
        saveCallCount += 1
        if let error = saveErrorToThrow { throw error }
        savedSnapshot = snapshot
    }

    func restore() async throws -> AppSessionSnapshot? {
        restoreCallCount += 1
        return try restoreResult.get()
    }

    func clear() async throws {
        clearCallCount += 1
    }

    // MARK: - Scrollback (no-op)

    func saveScrollback(_ content: String, for sessionId: UUID) async throws {}
    func loadScrollback(for sessionId: UUID) async -> String? { nil }
    func deleteScrollback(for sessionId: UUID) async throws {}
    func pruneOrphanedScrollbacks(keeping activeSessionIds: Set<UUID>) async throws -> Int { 0 }
}

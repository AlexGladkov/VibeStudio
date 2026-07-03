import Foundation
@testable import VibeStudio

/// Mock implementation of ``SessionPersisting`` for unit tests.
///
/// Implemented as an `actor` because every protocol method is `async` and the
/// mock keeps mutable verification state (`savedSnapshot`, call counts) that
/// must be safe to touch across suspension points.
///
/// - Configure `restoreResult` (init parameter or ``setRestoreResult(_:)``)
///   to control what ``restore()`` returns.
/// - Inspect `savedSnapshot` after ``save(snapshot:)`` to verify the captured state.
///
/// - Note: The domain snapshot type is ``AppSessionSnapshot`` (the task brief
///   referred to it as `SessionSnapshot`).
actor MockSessionPersisting: SessionPersisting {

    // MARK: - Configurable / Verification State

    /// Value returned by ``restore()``. `nil` simulates first launch / no snapshot.
    var restoreResult: AppSessionSnapshot?

    /// Last snapshot handed to ``save(snapshot:)`` — for test assertions.
    private(set) var savedSnapshot: AppSessionSnapshot?

    private(set) var saveCallCount = 0
    private(set) var restoreCallCount = 0
    private(set) var clearCallCount = 0

    // MARK: - Meta (nonisolated: immutable Sendable lets satisfy sync requirements)

    nonisolated let storageDirectory = URL(fileURLWithPath: "/tmp/vibestudio-tests")
    nonisolated let currentSnapshotVersion = 1

    // MARK: - Init

    init(restoreResult: AppSessionSnapshot? = nil) {
        self.restoreResult = restoreResult
    }

    /// Stage the snapshot returned by the next ``restore()`` call.
    func setRestoreResult(_ snapshot: AppSessionSnapshot?) {
        restoreResult = snapshot
    }

    // MARK: - Snapshot

    func save(snapshot: AppSessionSnapshot) async throws {
        saveCallCount += 1
        savedSnapshot = snapshot
    }

    func restore() async throws -> AppSessionSnapshot? {
        restoreCallCount += 1
        return restoreResult
    }

    func clear() async throws {
        clearCallCount += 1
        savedSnapshot = nil
    }

    // MARK: - Scrollback (no-op)

    func saveScrollback(_ content: String, for sessionId: UUID) async throws {}

    func loadScrollback(for sessionId: UUID) async -> String? { nil }

    func deleteScrollback(for sessionId: UUID) async throws {}

    @discardableResult
    func pruneOrphanedScrollbacks(keeping activeSessionIds: Set<UUID>) async throws -> Int { 0 }
}

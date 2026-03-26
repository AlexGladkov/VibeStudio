// MARK: - SessionStore
// Async file I/O for application session persistence.
// macOS 14+, Swift 5.10

import Foundation

/// Persists application session state (active project, terminal layouts, scrollback).
///
/// Storage location: `~/Library/Application Support/VibeStudio/`
/// - `session.json` -- application state snapshot
/// - `scrollback/<sessionId>.txt` -- terminal scrollback buffers
///
/// Version field enables graceful migration when the schema changes.
actor SessionStore: SessionPersisting {

    // MARK: - SessionPersisting

    let storageDirectory: URL
    nonisolated let currentSnapshotVersion: Int = 1

    // MARK: - Private

    private let snapshotURL: URL
    private let scrollbackDir: URL

    // MARK: - Init

    /// Creates a new `SessionStore`.
    ///
    /// - Parameter storageDirectory: Override directory for persistence files.
    ///   Pass a temporary directory in unit tests to avoid touching the real
    ///   Application Support folder. When `nil` (default), uses the standard
    ///   `~/Library/Application Support/VibeStudio/` path.
    init(storageDirectory overrideDir: URL? = nil) {
        let appDir: URL
        if let overrideDir {
            appDir = overrideDir
        } else {
            guard let systemDir = try? PathConstants.appSupportDirectory else {
                fatalError("[VibeStudio] Application Support directory not found")
            }
            appDir = systemDir
        }

        self.storageDirectory = appDir
        self.snapshotURL = appDir.appendingPathComponent("session.json")
        self.scrollbackDir = appDir.appendingPathComponent("scrollback")

        // Ensure scrollback subdirectory exists.
        try? FileManager.default.createDirectory(
            at: scrollbackDir,
            withIntermediateDirectories: true,
            attributes: [.posixPermissions: 0o700]
        )
    }

    // MARK: - Snapshot

    func save(snapshot: AppSessionSnapshot) async throws {
        do {
            let encoder = JSONEncoder()
            encoder.outputFormatting = [.prettyPrinted]
            encoder.dateEncodingStrategy = .iso8601
            let data = try encoder.encode(snapshot)
            try data.write(to: snapshotURL, options: .atomic)
        } catch {
            throw SessionPersistenceError.encodingFailed(underlying: error)
        }
    }

    func restore() async throws -> AppSessionSnapshot? {
        let fm = FileManager.default
        guard fm.fileExists(atPath: snapshotURL.path) else {
            return nil
        }

        do {
            let data = try Data(contentsOf: snapshotURL)
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            let snapshot = try decoder.decode(AppSessionSnapshot.self, from: data)

            // Version check for graceful degradation.
            guard snapshot.version <= currentSnapshotVersion else {
                throw SessionPersistenceError.incompatibleVersion(
                    found: snapshot.version,
                    expected: currentSnapshotVersion
                )
            }

            return snapshot
        } catch let error as SessionPersistenceError {
            throw error
        } catch {
            throw SessionPersistenceError.decodingFailed(underlying: error)
        }
    }

    func clear() async throws {
        let fm = FileManager.default
        if fm.fileExists(atPath: snapshotURL.path) {
            try fm.removeItem(at: snapshotURL)
        }
    }

    // MARK: - Scrollback

    func saveScrollback(_ content: String, for sessionId: UUID) async throws {
        let fileURL = scrollbackDir.appendingPathComponent("\(sessionId.uuidString).txt")
        do {
            let data = Data(content.utf8)
            try data.write(to: fileURL, options: .atomic)
            // Set restrictive permissions - scrollback may contain sensitive terminal output
            try? FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: fileURL.path)
        } catch {
            throw SessionPersistenceError.scrollbackWriteFailed(
                sessionId: sessionId,
                underlying: error
            )
        }
    }

    func loadScrollback(for sessionId: UUID) async -> String? {
        let fileURL = scrollbackDir.appendingPathComponent("\(sessionId.uuidString).txt")
        return try? String(contentsOf: fileURL, encoding: .utf8)
    }

    func deleteScrollback(for sessionId: UUID) async throws {
        let fileURL = scrollbackDir.appendingPathComponent("\(sessionId.uuidString).txt")
        let fm = FileManager.default
        if fm.fileExists(atPath: fileURL.path) {
            try fm.removeItem(at: fileURL)
        }
    }

    @discardableResult
    func pruneOrphanedScrollbacks(keeping activeSessionIds: Set<UUID>) async throws -> Int {
        let fm = FileManager.default
        guard let files = try? fm.contentsOfDirectory(
            at: scrollbackDir,
            includingPropertiesForKeys: nil
        ) else {
            return 0
        }

        var pruned = 0
        for file in files where file.pathExtension == "txt" {
            let name = file.deletingPathExtension().lastPathComponent
            if let id = UUID(uuidString: name), !activeSessionIds.contains(id) {
                try? fm.removeItem(at: file)
                pruned += 1
            }
        }

        return pruned
    }
}

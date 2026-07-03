// MARK: - FileEditorViewModel
// File I/O backing the plain editor sheets (text file, CLAUDE.md, skill viewer).
// macOS 14+, Swift 5.10

import Foundation
import Observation

// MARK: - FileEditorViewModel

/// Loads and saves a single plain-text file for the simple editor sheets.
///
/// Owns the file I/O that previously lived inside the views: reading the file
/// (falling back to `defaultContent` when it does not exist yet), optionally
/// creating the parent directory, and writing atomically. Sheets with extra
/// validation (command / agent) use dedicated view models instead.
@Observable
@MainActor
final class FileEditorViewModel {

    // MARK: - State

    /// Current editor text, two-way bound by the view.
    var content: String = ""
    /// Text as it exists on disk after the last successful load/save.
    private(set) var savedContent: String = ""
    /// Localised save error, or `nil` when the last save succeeded.
    private(set) var saveError: String?

    /// Whether the buffer differs from what is persisted.
    var hasUnsavedChanges: Bool { content != savedContent }

    // MARK: - Configuration

    let fileURL: URL
    private let defaultContent: String
    private let createsDirectory: Bool

    // MARK: - Init

    /// - Parameters:
    ///   - fileURL: File to load and save.
    ///   - defaultContent: Text used when `fileURL` does not exist yet.
    ///   - createsDirectory: Create the parent directory before writing.
    ///     `false` for files whose directory always exists (e.g. skills).
    init(fileURL: URL, defaultContent: String = "", createsDirectory: Bool = true) {
        self.fileURL = fileURL
        self.defaultContent = defaultContent
        self.createsDirectory = createsDirectory
    }

    // MARK: - File I/O

    func load() {
        let text = (try? String(contentsOf: fileURL, encoding: .utf8)) ?? defaultContent
        content = text
        savedContent = text
    }

    /// Writes the current buffer to disk.
    /// - Returns: `true` on success so the view can run its dismiss/refresh hook.
    @discardableResult
    func save() -> Bool {
        saveError = nil
        do {
            if createsDirectory {
                let dir = fileURL.deletingLastPathComponent()
                try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            }
            try content.write(to: fileURL, atomically: true, encoding: .utf8)
            savedContent = content
            return true
        } catch {
            saveError = "Ошибка: \(error.localizedDescription)"
            return false
        }
    }
}

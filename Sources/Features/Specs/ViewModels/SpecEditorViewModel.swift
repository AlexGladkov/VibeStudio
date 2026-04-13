// MARK: - SpecEditorViewModel
// Manages loading and saving a single .cs.md spec file.
// macOS 14+, Swift 5.10

import Foundation
import Observation
import OSLog

/// ViewModel for `SpecEditorSheet`.
///
/// Loads the spec file content on init, tracks edits, and saves back to disk.
@Observable
@MainActor
final class SpecEditorViewModel {

    // MARK: - State

    /// Current editor content (two-way bound to the NSTextView).
    var content: String = ""

    /// Whether unsaved changes exist.
    private(set) var isDirty = false

    /// Error message from the last save attempt.
    private(set) var saveError: String?

    /// True while saving.
    private(set) var isSaving = false

    // MARK: - Private

    private let fileURL: URL

    // MARK: - Init

    init(specFile: SpecFile) {
        self.fileURL = specFile.url
        self.content = (try? String(contentsOf: specFile.url, encoding: .utf8)) ?? ""
    }

    // MARK: - Actions

    /// Mark content as changed.
    func markDirty() {
        isDirty = true
        saveError = nil
    }

    /// Save current content back to the spec file.
    func save() async -> Bool {
        guard isDirty else { return true }
        isSaving = true
        saveError = nil
        defer { isSaving = false }

        do {
            try Data(content.utf8).write(to: fileURL, options: .atomic)
            isDirty = false
            Logger.services.info("SpecEditorViewModel: saved \(self.fileURL.lastPathComponent, privacy: .public)")
            return true
        } catch {
            saveError = "Save failed: \(error.localizedDescription)"
            Logger.services.error("SpecEditorViewModel: \(error.localizedDescription, privacy: .public)")
            return false
        }
    }
}

// MARK: - CommandEditorViewModel
// File I/O + filename validation for the Claude command editor sheet.
// macOS 14+, Swift 5.10

import Foundation
import Observation

// MARK: - CommandEditorViewModel

/// Backs ``CommandEditorSheet``: edits an existing command file in place, or
/// creates a new `.md` file in `~/.claude/commands/` after validating the
/// user-supplied filename.
@Observable
@MainActor
final class CommandEditorViewModel {

    // MARK: - Save Outcome

    /// Result of a successful ``save()``.
    enum SaveOutcome {
        /// Existing file overwritten — keep the sheet open.
        case saved
        /// New file created — the sheet should dismiss.
        case savedAndDismiss
    }

    // MARK: - Filename Validation

    /// Reason a user-supplied command filename was rejected.
    ///
    /// Rejecting anything outside `[a-z0-9_-]` is the primary defence against
    /// path-traversal: names like `../etc` or `foo/bar` fail ``invalidCharacters``.
    enum FilenameValidationError: Error, Equatable {
        /// Name was empty after trimming whitespace.
        case empty
        /// Name contained characters outside `[a-z0-9_-]`.
        case invalidCharacters
    }

    // MARK: - State

    var content: String = ""
    var filename: String = ""
    private(set) var savedContent: String = ""
    private(set) var saveError: String?

    var hasUnsavedChanges: Bool { content != savedContent }

    // MARK: - Configuration

    /// File to edit, or `nil` to create a new command.
    let fileURL: URL?
    var isNewFile: Bool { fileURL == nil }

    /// Directory new command files are written to. Injectable for testing;
    /// defaults to the real `~/.claude/commands/`.
    let commandsDirectoryURL: URL

    // MARK: - Constants

    /// Real on-disk commands directory used in production.
    static let defaultCommandsDirectoryURL: URL = FileManager.default
        .homeDirectoryForCurrentUser
        .appendingPathComponent(".claude/commands")

    private static let newCommandTemplate = "# Название команды\n\nОписание что делает команда...\n"

    /// Characters allowed in a command filename.
    private static let filenameRegex = /^[a-z0-9_\-]+$/

    // MARK: - Init

    init(fileURL: URL?, commandsDirectoryURL: URL = CommandEditorViewModel.defaultCommandsDirectoryURL) {
        self.fileURL = fileURL
        self.commandsDirectoryURL = commandsDirectoryURL
    }

    // MARK: - Validation

    /// Validates a user-supplied command filename (without extension).
    ///
    /// Trims surrounding whitespace, then requires a non-empty string matching
    /// `^[a-z0-9_-]+$`. Returns the trimmed name on success.
    static func validateFilename(_ name: String) -> Result<String, FilenameValidationError> {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return .failure(.empty) }
        guard (try? filenameRegex.wholeMatch(in: trimmed)) != nil else {
            return .failure(.invalidCharacters)
        }
        return .success(trimmed)
    }

    // MARK: - File I/O

    func load() {
        if let url = fileURL {
            let text = (try? String(contentsOf: url, encoding: .utf8)) ?? ""
            content = text
            savedContent = text
        } else {
            content = Self.newCommandTemplate
            savedContent = ""
        }
    }

    /// Persists the command. Returns `nil` when validation fails or the write
    /// throws (in which case ``saveError`` is populated).
    func save() -> SaveOutcome? {
        saveError = nil

        if let url = fileURL {
            // Editing existing file — overwrite in place.
            do {
                try content.write(to: url, atomically: true, encoding: .utf8)
                savedContent = content
                return .saved
            } catch {
                saveError = String(localized: "Error: \(error.localizedDescription)")
                return nil
            }
        }

        // New command — validate filename, write new file.
        let trimmed: String
        switch Self.validateFilename(filename) {
        case .success(let name):
            trimmed = name
        case .failure(.empty):
            saveError = String(localized: "Enter a file name")
            return nil
        case .failure(.invalidCharacters):
            saveError = String(localized: "Only lowercase Latin letters, digits, hyphen, and underscore are allowed")
            return nil
        }

        let dir = commandsDirectoryURL
        let targetURL = dir.appendingPathComponent("\(trimmed).md")

        guard !FileManager.default.fileExists(atPath: targetURL.path) else {
            saveError = String(localized: "A file named “\(trimmed).md” already exists")
            return nil
        }

        do {
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            try content.write(to: targetURL, atomically: true, encoding: .utf8)
            savedContent = content
            return .savedAndDismiss
        } catch {
            saveError = String(localized: "Error: \(error.localizedDescription)")
            return nil
        }
    }
}

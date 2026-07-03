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

    // MARK: - Constants

    private static let commandsDirectoryURL: URL = FileManager.default
        .homeDirectoryForCurrentUser
        .appendingPathComponent(".claude/commands")

    private static let newCommandTemplate = "# Название команды\n\nОписание что делает команда...\n"

    /// Characters allowed in a command filename.
    private static let filenameRegex = /^[a-z0-9_\-]+$/

    // MARK: - Init

    init(fileURL: URL?) {
        self.fileURL = fileURL
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
                saveError = "Ошибка: \(error.localizedDescription)"
                return nil
            }
        }

        // New command — validate filename, write new file.
        let trimmed = filename.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else {
            saveError = "Введите имя файла"
            return nil
        }
        guard (try? Self.filenameRegex.wholeMatch(in: trimmed)) != nil else {
            saveError = "Допустимы только строчные латинские буквы, цифры, дефис и подчёркивание"
            return nil
        }

        let dir = Self.commandsDirectoryURL
        let targetURL = dir.appendingPathComponent("\(trimmed).md")

        guard !FileManager.default.fileExists(atPath: targetURL.path) else {
            saveError = "Файл «\(trimmed).md» уже существует"
            return nil
        }

        do {
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            try content.write(to: targetURL, atomically: true, encoding: .utf8)
            savedContent = content
            return .savedAndDismiss
        } catch {
            saveError = "Ошибка: \(error.localizedDescription)"
            return nil
        }
    }
}

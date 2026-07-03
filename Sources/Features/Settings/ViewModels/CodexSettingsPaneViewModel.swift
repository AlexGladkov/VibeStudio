// MARK: - CodexSettingsPaneViewModel
// Observable view model for the Codex settings pane.
// Manages memories and skills data loading and deletion.
// macOS 14+, Swift 5.10

import Foundation
import OSLog

// MARK: - CodexMemoryEntry

/// Represents a single Markdown memory file in `~/.codex/memories/`.
struct CodexMemoryEntry: Identifiable {
    /// File path used as stable identity.
    let id: String
    let fileURL: URL
    let filename: String

    /// Display name (filename without `.md` extension).
    var displayName: String {
        fileURL.deletingPathExtension().lastPathComponent
    }
}

// MARK: - CodexSkillEntry

/// Represents a skill directory in `~/.codex/skills/`.
struct CodexSkillEntry: Identifiable {
    /// Directory path used as stable identity.
    let id: String
    let directoryURL: URL

    /// Display name (last path component).
    var displayName: String { directoryURL.lastPathComponent }
}

// MARK: - CodexSettingsPaneViewModel

/// Manages memories and skills state for ``CodexSettingsPane``.
///
/// Encapsulates all FileManager operations (directory listing, deletion) so the
/// view only handles UI concerns.
@Observable
@MainActor
final class CodexSettingsPaneViewModel {

    // MARK: - Published State

    /// Loaded memory entries from `~/.codex/memories/`.
    var memories: [CodexMemoryEntry] = []

    /// Loaded skill entries from `~/.codex/skills/`.
    var skills: [CodexSkillEntry] = []

    // MARK: - Constants

    static let configURL: URL = FileManager.default
        .homeDirectoryForCurrentUser
        .appendingPathComponent(".codex/config.toml")

    static let memoriesURL: URL = FileManager.default
        .homeDirectoryForCurrentUser
        .appendingPathComponent(".codex/memories")

    private static let skillsURL: URL = FileManager.default
        .homeDirectoryForCurrentUser
        .appendingPathComponent(".codex/skills")

    /// Display path of the config file with home directory replaced by `~`.
    var displayPath: String {
        Self.configURL.tildeAbbreviatedPath
    }

    // MARK: - Data Loading -- Memories

    /// Scans `~/.codex/memories/` for markdown files.
    func loadMemories() {
        memories = AgentDirectoryLoader.loadFiles(from: Self.memoriesURL, extension: "md") { url in
            CodexMemoryEntry(
                id: url.path,
                fileURL: url,
                filename: url.lastPathComponent
            )
        }
    }

    // MARK: - Data Loading -- Skills

    /// Scans `~/.codex/skills/` for subdirectories.
    func loadSkills() {
        let fileManager = FileManager.default
        let dir = Self.skillsURL
        guard let contents = try? fileManager.contentsOfDirectory(
            at: dir,
            includingPropertiesForKeys: [.isDirectoryKey],
            options: [.skipsHiddenFiles]
        ) else {
            Logger.settings.debug("CodexSettings: skills dir not readable at \(dir.path, privacy: .public)")
            skills = []
            return
        }

        skills = contents
            .filter { url in
                (try? url.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) == true
            }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { url in
                CodexSkillEntry(id: url.path, directoryURL: url)
            }
    }

    // MARK: - Delete

    /// Deletes the memory file from disk and reloads the memory list.
    ///
    /// - Parameter memory: The memory entry to remove.
    func deleteMemory(_ memory: CodexMemoryEntry) {
        do {
            try FileManager.default.removeItem(at: memory.fileURL)
        } catch {
            let name = memory.filename
            Logger.settings.error(
                "CodexSettings: failed to delete memory \(name, privacy: .public): \(error.localizedDescription, privacy: .public)"
            )
        }
        loadMemories()
    }
}

// MARK: - QwenSettingsPaneViewModel
// Observable view model for the Qwen settings pane.
// Manages config-exists probing and agent loading/deletion.
// macOS 14+, Swift 5.10

import Foundation
import OSLog

// MARK: - QwenSettingsPaneViewModel

/// Manages state for ``QwenSettingsPane``: global config probing and agent CRUD.
///
/// Encapsulates all FileManager operations so the view only handles UI concerns.
@Observable
@MainActor
final class QwenSettingsPaneViewModel {

    // MARK: - Published State

    /// Whether `~/.qwen/QWEN.md` exists on disk.
    var configExists: Bool = false

    /// Loaded agent entries from `~/.qwen/agents/`.
    var agents: [AgentEntry] = []

    // MARK: - Constants

    static let qwenURL: URL = FileManager.default
        .homeDirectoryForCurrentUser
        .appendingPathComponent(".qwen/QWEN.md")

    static let agentsDirectoryURL: URL = FileManager.default
        .homeDirectoryForCurrentUser
        .appendingPathComponent(".qwen/agents")

    /// Display path of the global config with home directory replaced by `~`.
    var displayPath: String {
        Self.qwenURL.tildeAbbreviatedPath
    }

    // MARK: - Probes

    /// Refreshes ``configExists`` from disk.
    func checkConfigExists() {
        configExists = FileManager.default.fileExists(atPath: Self.qwenURL.path)
    }

    // MARK: - Data Loading

    /// Scans `~/.qwen/agents/` for markdown files and parses their frontmatter.
    func loadAgents() {
        agents = AgentDirectoryLoader.load(from: Self.agentsDirectoryURL) { url, fields in
            AgentEntry(
                id: url.path,
                fileURL: url,
                name: fields.name.isEmpty ? url.deletingPathExtension().lastPathComponent : fields.name,
                description: fields.description
            )
        }
    }

    // MARK: - Delete

    /// Deletes the agent file from disk and reloads the agent list.
    ///
    /// - Parameter agent: The agent entry to remove.
    func deleteAgent(_ agent: AgentEntry) {
        do {
            try FileManager.default.removeItem(at: agent.fileURL)
        } catch {
            let name = agent.fileURL.lastPathComponent
            Logger.settings.error(
                "QwenSettings: failed to delete agent \(name, privacy: .public): \(error.localizedDescription, privacy: .public)"
            )
        }
        loadAgents()
    }
}

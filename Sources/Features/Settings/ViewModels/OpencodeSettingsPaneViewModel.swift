// MARK: - OpencodeSettingsPaneViewModel
// Observable view model for the opencode settings pane.
// Manages TypeScript plugin loading and deletion.
// macOS 14+, Swift 5.10

import Foundation
import OSLog

// MARK: - OpencodePluginEntry

/// Represents a single TypeScript plugin file in `~/.config/opencode/plugins/`.
struct OpencodePluginEntry: Identifiable {
    /// File path used as stable identity.
    let id: String
    let fileURL: URL
    let filename: String

    /// Display name (filename without `.ts` extension).
    var displayName: String {
        fileURL.deletingPathExtension().lastPathComponent
    }
}

// MARK: - OpencodeSettingsPaneViewModel

/// Manages plugin state for ``OpencodeSettingsPane``.
///
/// Encapsulates all FileManager operations (directory listing, deletion) so the
/// view only handles UI concerns.
@Observable
@MainActor
final class OpencodeSettingsPaneViewModel {

    // MARK: - Published State

    /// Loaded plugin entries from `~/.config/opencode/plugins/`.
    var plugins: [OpencodePluginEntry] = []

    // MARK: - Constants

    static let configDirectoryURL: URL = FileManager.default
        .homeDirectoryForCurrentUser
        .appendingPathComponent(".config/opencode")

    static let pluginsDirectoryURL: URL = FileManager.default
        .homeDirectoryForCurrentUser
        .appendingPathComponent(".config/opencode/plugins")

    /// Display path of the config directory with home directory replaced by `~`.
    var displayConfigPath: String {
        Self.configDirectoryURL.tildeAbbreviatedPath
    }

    // MARK: - Data Loading

    /// Scans `~/.config/opencode/plugins/` for TypeScript files.
    func loadPlugins() {
        let fileManager = FileManager.default
        let dir = Self.pluginsDirectoryURL
        guard let contents = try? fileManager.contentsOfDirectory(
            at: dir,
            includingPropertiesForKeys: [.nameKey],
            options: [.skipsHiddenFiles]
        ) else {
            Logger.settings.debug("OpencodeSettings: plugins dir not readable at \(dir.path, privacy: .public)")
            plugins = []
            return
        }

        plugins = contents
            .filter { $0.pathExtension == "ts" }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { url in
                OpencodePluginEntry(
                    id: url.path,
                    fileURL: url,
                    filename: url.lastPathComponent
                )
            }
    }

    // MARK: - Delete

    /// Deletes the plugin file from disk and reloads the plugin list.
    ///
    /// - Parameter plugin: The plugin entry to remove.
    func deletePlugin(_ plugin: OpencodePluginEntry) {
        do {
            try FileManager.default.removeItem(at: plugin.fileURL)
        } catch {
            let name = plugin.filename
            Logger.settings.error(
                "OpencodeSettings: failed to delete plugin \(name, privacy: .public): \(error.localizedDescription, privacy: .public)"
            )
        }
        loadPlugins()
    }
}

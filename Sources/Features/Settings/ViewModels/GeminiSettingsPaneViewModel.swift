// MARK: - GeminiSettingsPaneViewModel
// Observable view model for the Gemini settings pane.
// Manages config-exists probing for ~/.gemini/settings.json.
// macOS 14+, Swift 5.10

import Foundation

// MARK: - GeminiSettingsPaneViewModel

/// Manages state for ``GeminiSettingsPane``: probing whether the global
/// `~/.gemini/settings.json` config exists on disk.
///
/// Encapsulates the FileManager probe so the view only handles UI concerns,
/// matching the pattern used by ``QwenSettingsPaneViewModel``.
@Observable
@MainActor
final class GeminiSettingsPaneViewModel {

    // MARK: - Published State

    /// Whether `~/.gemini/settings.json` exists on disk.
    var configExists: Bool = false

    // MARK: - Constants

    static let settingsURL: URL = FileManager.default
        .homeDirectoryForCurrentUser
        .appendingPathComponent(".gemini/settings.json")

    /// Display path of the settings file with the home directory replaced by `~`.
    var displayPath: String {
        Self.settingsURL.tildeAbbreviatedPath
    }

    // MARK: - Probes

    /// Refreshes ``configExists`` from disk.
    func checkConfigExists() {
        configExists = FileManager.default.fileExists(atPath: Self.settingsURL.path)
    }
}

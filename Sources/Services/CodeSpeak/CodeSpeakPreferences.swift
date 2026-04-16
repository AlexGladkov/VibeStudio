// MARK: - CodeSpeakPreferences
// UserDefaults-backed preferences for CodeSpeak behaviour.
// macOS 14+, Swift 5.10

import Foundation
import Observation
import UserNotifications
import OSLog

/// UserDefaults-backed preferences for CodeSpeak in-app behaviour.
///
/// All properties are persisted immediately on change via `didSet`.
/// Keys are prefixed with `cs_` to avoid collisions with other services.
///
/// Injected via `@Environment(\.csPreferences)` — concrete type for
/// `@Observable` tracking (same pattern as `ThemeService`).
@Observable
@MainActor
final class CodeSpeakPreferences {

    private let defaults = UserDefaults.standard

    private enum Keys {
        static let autoBuildOnSave    = "cs_auto_build_on_save"
        static let buildOnProjectOpen = "cs_build_on_open"
        static let autoOpenPanel      = "cs_auto_open_panel"
        static let defaultCommand     = "cs_default_command"
        static let notifyOnComplete   = "cs_notify_on_complete"
        static let showFailingOnly    = "cs_show_failing_only"
    }

    // MARK: - Preferences

    /// Automatically run `codespeak build` after saving a spec file. Default: `false`.
    var autoBuildOnSave: Bool {
        didSet { defaults.set(autoBuildOnSave, forKey: Keys.autoBuildOnSave) }
    }

    /// Run `codespeak build` when switching to a CodeSpeak project. Default: `false`.
    var buildOnProjectOpen: Bool {
        didSet { defaults.set(buildOnProjectOpen, forKey: Keys.buildOnProjectOpen) }
    }

    /// Automatically open the build panel when a command starts. Default: `true`.
    var autoOpenBuildPanel: Bool {
        didSet { defaults.set(autoOpenBuildPanel, forKey: Keys.autoOpenPanel) }
    }

    /// Default command executed when pressing ▶. Default: `.build`.
    var defaultCommand: CodeSpeakCommand {
        didSet { defaults.set(defaultCommand.rawValue, forKey: Keys.defaultCommand) }
    }

    /// Send a macOS notification when a command completes. Default: `false`.
    var notifyOnComplete: Bool {
        didSet {
            defaults.set(notifyOnComplete, forKey: Keys.notifyOnComplete)
            if notifyOnComplete { requestNotificationPermission() }
        }
    }

    /// Show only failing specs in the spec list. Default: `false`.
    var showFailingOnly: Bool {
        didSet { defaults.set(showFailingOnly, forKey: Keys.showFailingOnly) }
    }

    // MARK: - Init

    init() {
        autoBuildOnSave    = defaults.bool(forKey: Keys.autoBuildOnSave)
        buildOnProjectOpen = defaults.bool(forKey: Keys.buildOnProjectOpen)
        // autoOpenPanel defaults to true on first launch (key absent → nil → true)
        autoOpenBuildPanel = defaults.object(forKey: Keys.autoOpenPanel) == nil
            ? true
            : defaults.bool(forKey: Keys.autoOpenPanel)
        defaultCommand     = CodeSpeakCommand(rawValue: defaults.string(forKey: Keys.defaultCommand) ?? "") ?? .build
        notifyOnComplete   = defaults.bool(forKey: Keys.notifyOnComplete)
        showFailingOnly    = defaults.bool(forKey: Keys.showFailingOnly)
    }

    // MARK: - Notification Helpers

    /// Request macOS notification authorization.
    ///
    /// If the user denies permission, `notifyOnComplete` is reset to `false`
    /// so the settings pane reflects the actual system state.
    private func requestNotificationPermission() {
        UNUserNotificationCenter.current()
            .requestAuthorization(options: [.alert, .sound]) { granted, error in
                if let error {
                    Logger.services.error(
                        "CodeSpeakPreferences: notification auth error: \(error.localizedDescription, privacy: .public)"
                    )
                }
                if !granted {
                    Task { @MainActor [weak self] in
                        self?.notifyOnComplete = false
                    }
                }
            }
    }

    /// Post a completion notification for the finished command.
    ///
    /// No-op when `notifyOnComplete` is `false` or the run was cancelled.
    func sendCompletionNotification(command: CodeSpeakCommand, exitCode: Int32?, wasCancelled: Bool) {
        guard notifyOnComplete, !wasCancelled else { return }
        let success = exitCode == 0
        let title = "CodeSpeak \(command.displayName) \(success ? "passed ✓" : "failed ✗")"
        let body: String
        switch command {
        case .build: body = success ? "All specs passing."         : "Some specs are failing."
        case .impl:  body = success ? "Implementation complete."   : "Implementation failed."
        case .run:   body = success ? "Run completed successfully." : "Run finished with errors."
        case .test:  body = success ? "All tests passed."          : "Some tests failed."
        default:     body = success ? "Completed."                 : "Finished with errors."
        }

        let content = UNMutableNotificationContent()
        content.title = title
        content.body  = body
        content.sound = .default

        let request = UNNotificationRequest(
            identifier: "cs-complete-\(UUID().uuidString)",
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request) { error in
            if let error {
                Logger.services.error(
                    "CodeSpeakPreferences: notification delivery error: \(error.localizedDescription, privacy: .public)"
                )
            }
        }
    }
}

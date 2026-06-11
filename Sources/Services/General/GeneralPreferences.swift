// MARK: - GeneralPreferences
// UserDefaults-backed general app preferences.
// macOS 14+, Swift 5.10

import CoreGraphics
import Foundation
import Observation

/// UserDefaults-backed general application preferences.
///
/// Keys are prefixed with `vs_` to avoid collisions with other services.
/// Injected via `@Environment(\.generalPreferences)` — concrete type for
/// `@Observable` tracking (same pattern as `ThemeService`).
@Observable
@MainActor
final class GeneralPreferences {

    private let defaults = UserDefaults.standard

    private enum Keys {
        static let confirmTabClose = "vs_confirm_tab_close"
        static let terminalFontSize = "vs_terminal_font_size"
        static let claudeSkipPermissions = "vs_claude_skip_permissions"
    }

    // MARK: - Preferences

    /// Show a confirmation alert before closing a tab. Default: `true`.
    var confirmTabClose: Bool {
        didSet { defaults.set(confirmTabClose, forKey: Keys.confirmTabClose) }
    }

    /// Launch Claude with `--dangerously-skip-permissions`. Default: `false`.
    var claudeSkipPermissions: Bool {
        didSet { defaults.set(claudeSkipPermissions, forKey: Keys.claudeSkipPermissions) }
    }

    /// Terminal font size in points. Default: `13`. Range: 9…24.
    var terminalFontSize: CGFloat {
        didSet {
            let clamped = min(max(terminalFontSize, 9), 24)
            if clamped != terminalFontSize { terminalFontSize = clamped }
            defaults.set(Double(clamped), forKey: Keys.terminalFontSize)
        }
    }

    // MARK: - Init

    init() {
        // confirmTabClose defaults to true on first launch (key absent → nil → true)
        confirmTabClose = defaults.object(forKey: Keys.confirmTabClose) == nil
            ? true
            : defaults.bool(forKey: Keys.confirmTabClose)

        // claudeSkipPermissions defaults to false on first launch
        claudeSkipPermissions = defaults.bool(forKey: Keys.claudeSkipPermissions)

        // terminalFontSize defaults to 13pt on first launch
        terminalFontSize = defaults.object(forKey: Keys.terminalFontSize) == nil
            ? 13
            : CGFloat(defaults.double(forKey: Keys.terminalFontSize))
    }

    /// Preview / EnvironmentKey-default factory.
    ///
    /// Uses fixed in-memory defaults instead of reading `UserDefaults`. Writes
    /// via the `didSet` observers still hit standard UserDefaults — callers must
    /// treat the returned instance as read-only.
    static func previewStub() -> GeneralPreferences {
        GeneralPreferences()
    }
}

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
        /// Feature flag: show cost tracker badge in toolbar. Default: true.
        /// Gates UI display and WS broadcast only — parsing always runs.
        /// Pro-gate placeholder: when licensing is added, this key is controlled externally.
        static let costTrackerEnabled = "vs_cost_tracker_enabled"
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

    /// Show the cost tracker badge in the toolbar. Default: `true`.
    ///
    /// This flag gates only the UI display and WS broadcast — the parsing and
    /// accumulation always runs so data is available instantly when re-enabled.
    /// Future Pro-gate: set this externally via StoreKit receipt validation.
    var costTrackerEnabled: Bool {
        didSet { defaults.set(costTrackerEnabled, forKey: Keys.costTrackerEnabled) }
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

        // costTrackerEnabled defaults to true on first launch (key absent → nil → true)
        costTrackerEnabled = defaults.object(forKey: Keys.costTrackerEnabled) == nil
            ? true
            : defaults.bool(forKey: Keys.costTrackerEnabled)

        // terminalFontSize defaults to 13pt on first launch
        terminalFontSize = defaults.object(forKey: Keys.terminalFontSize) == nil
            ? 13
            : CGFloat(defaults.double(forKey: Keys.terminalFontSize))
    }
}

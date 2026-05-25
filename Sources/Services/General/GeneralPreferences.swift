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
    }

    // MARK: - Preferences

    /// Show a confirmation alert before closing a tab. Default: `true`.
    var confirmTabClose: Bool {
        didSet { defaults.set(confirmTabClose, forKey: Keys.confirmTabClose) }
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

        // terminalFontSize defaults to 13pt on first launch
        terminalFontSize = defaults.object(forKey: Keys.terminalFontSize) == nil
            ? 13
            : CGFloat(defaults.double(forKey: Keys.terminalFontSize))
    }
}

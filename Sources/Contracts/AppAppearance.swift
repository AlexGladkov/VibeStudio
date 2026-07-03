// MARK: - AppAppearance
// App-wide appearance/theme options.
// macOS 14+, Swift 5.10

import AppKit

// MARK: - AppAppearance

/// App-wide appearance options.
enum AppAppearance: Int, CaseIterable {
    case system = 0
    case dark   = 1
    case light  = 2

    /// Human-readable display name for settings UI.
    var displayName: String {
        switch self {
        case .system: return "System"
        case .dark:   return "Dark"
        case .light:  return "Light"
        }
    }

    /// Resolved `NSAppearance.Name` to apply to `NSApp.appearance`.
    /// `nil` means follow the system appearance.
    var nsAppearanceName: NSAppearance.Name? {
        switch self {
        case .system: return nil
        case .dark:   return .darkAqua
        case .light:  return .aqua
        }
    }
}

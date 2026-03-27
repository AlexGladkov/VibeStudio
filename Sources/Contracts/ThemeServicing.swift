// MARK: - ThemeServicing
// Protocol for app-wide appearance/theme management.
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

// MARK: - ThemeServicing

/// Manages app-wide appearance selection and applies it to `NSApp`.
///
/// Implementations must be `@MainActor` because `NSApp.appearance`
/// must be mutated on the main thread.
@MainActor
protocol ThemeServicing: AnyObject {
    /// The currently selected appearance preference.
    var selectedAppearance: AppAppearance { get }

    /// Change the selected appearance and apply it immediately.
    func setAppearance(_ appearance: AppAppearance)

    /// Apply the stored appearance without changing the stored value.
    /// Call this on app launch so the theme takes effect before the first
    /// render frame.
    func applyStoredAppearance()
}

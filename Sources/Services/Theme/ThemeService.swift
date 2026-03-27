// MARK: - ThemeService
// Stores and applies the user's preferred appearance (System / Dark / Light).
// macOS 14+, Swift 5.10

import AppKit
import Observation
import OSLog

// MARK: - Notification Name

extension Notification.Name {
    /// Posted when the user changes the app appearance.
    ///
    /// `object` is the new `AppAppearance` value.
    static let themeDidChange = Notification.Name("vs.themeDidChange")
}

// MARK: - ThemeService

/// Manages app-wide appearance selection and applies it to `NSApp`.
///
/// Reads/writes `vs_appearance` in `UserDefaults.standard`.
/// On change, sets `NSApp.appearance` — which propagates to **all** views
/// (both AppKit and SwiftUI via `NSHostingView`).
///
/// Post a `.themeDidChange` notification so secondary consumers (e.g.
/// `TerminalService`) can update their non-SwiftUI appearance.
@Observable
@MainActor
final class ThemeService: ThemeServicing {

    // MARK: - Stored State

    private let defaults = UserDefaults.standard
    private let storageKey = "vs_appearance"

    /// The currently selected appearance preference.
    ///
    /// Setting this property persists the value, applies `NSApp.appearance`,
    /// and notifies observers via `Notification.Name.themeDidChange`.
    var selectedAppearance: AppAppearance {
        didSet {
            defaults.set(selectedAppearance.rawValue, forKey: storageKey)
            apply(selectedAppearance)
            NotificationCenter.default.post(
                name: .themeDidChange,
                object: selectedAppearance
            )
        }
    }

    // MARK: - Init

    init() {
        let stored = defaults.integer(forKey: "vs_appearance")
        self.selectedAppearance = AppAppearance(rawValue: stored) ?? .system
    }

    // MARK: - ThemeServicing

    func setAppearance(_ appearance: AppAppearance) {
        selectedAppearance = appearance
    }

    func applyStoredAppearance() {
        apply(selectedAppearance)
    }

    // MARK: - Private

    private func apply(_ appearance: AppAppearance) {
        if let name = appearance.nsAppearanceName {
            NSApp.appearance = NSAppearance(named: name)
        } else {
            // nil = follow macOS System Preferences.
            NSApp.appearance = nil
        }
        Logger.ui.info(
            "ThemeService: applied appearance \(appearance.displayName, privacy: .public)"
        )
    }
}

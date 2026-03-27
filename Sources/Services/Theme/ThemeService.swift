// MARK: - ThemeService
// Stores and applies the user's preferred appearance (System / Dark / Light).
// macOS 14+, Swift 5.10

import AppKit
import Observation
import OSLog
import SwiftUI

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

    /// Bumped whenever the macOS system theme changes while `selectedAppearance == .system`.
    ///
    /// Accessing this in `resolvedColorScheme` creates an `@Observable` tracking
    /// dependency, so views re-render automatically when the system theme flips.
    private var systemThemeToken: Int = 0

    /// Retained observer for the macOS system-theme distributed notification.
    ///
    /// `nonisolated(unsafe)` for the same reason as `themeObserver` in
    /// `TerminalService`: `deinit` is non-isolated and we write only in `init`.
    nonisolated(unsafe) private var systemThemeObserver: NSObjectProtocol?

    // MARK: - ThemeServicing: resolvedColorScheme

    /// Always returns a concrete `.dark` or `.light` value.
    ///
    /// SwiftUI views use this in `.preferredColorScheme(_:)` instead of passing
    /// `nil` for the System case.  Passing `nil` would defer to the window's
    /// `effectiveAppearance` which updates via async KVO — causing a one-frame
    /// race where the sidebar renders in the old appearance while the terminal
    /// (updated via synchronous notification) already shows the new one.
    ///
    /// For `selectedAppearance == .system`, `NSApp.effectiveAppearance` is already
    /// up-to-date by the time this computed property is evaluated (because
    /// `NSApp.appearance = nil` was set synchronously in `apply()` before the
    /// SwiftUI re-render fires).  `systemThemeToken` is touched here so that
    /// @Observable tracks it: when the macOS system theme changes and we bump
    /// the token, every view reading `resolvedColorScheme` re-renders.
    var resolvedColorScheme: ColorScheme {
        _ = systemThemeToken  // declare @Observable dependency
        switch selectedAppearance {
        case .dark:   return .dark
        case .light:  return .light
        case .system:
            let isDark = NSApp.effectiveAppearance
                .bestMatch(from: [.darkAqua, .accessibilityHighContrastDarkAqua]) != nil
            return isDark ? .dark : .light
        }
    }

    // MARK: - Init

    init() {
        let stored = defaults.integer(forKey: "vs_appearance")
        self.selectedAppearance = AppAppearance(rawValue: stored) ?? .system

        // Observe macOS system dark/light toggle.
        // `AppleInterfaceThemeChangedNotification` is posted by the system via
        // DistributedNotificationCenter whenever the user changes Appearance in
        // System Preferences / System Settings (not an app-specific change).
        systemThemeObserver = DistributedNotificationCenter.default().addObserver(
            forName: .init("AppleInterfaceThemeChangedNotification"),
            object: nil,
            queue: .main
        ) { [weak self] _ in
            guard let self, self.selectedAppearance == .system else { return }
            // Bumping this triggers @Observable to re-render all dependents,
            // which re-evaluates `resolvedColorScheme` against the new
            // `NSApp.effectiveAppearance` value.
            self.systemThemeToken += 1
            NotificationCenter.default.post(
                name: .themeDidChange,
                object: AppAppearance.system
            )
        }
    }

    deinit {
        if let observer = systemThemeObserver {
            DistributedNotificationCenter.default().removeObserver(observer)
        }
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

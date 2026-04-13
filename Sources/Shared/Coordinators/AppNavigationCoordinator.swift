// MARK: - AppNavigationCoordinator
// Type-safe @Observable replacement for cross-component NotificationCenter events.
// macOS 14+, Swift 5.10

import Foundation
import Observation

enum AppMode: Equatable {
    case regular
    case codeSpeak
}

/// Coordinates global navigation actions (install wizard, settings) between
/// the toolbar (sender) and the root view (receiver).
///
/// Replaces two untyped `NotificationCenter` posts:
///  - `.showInstallAgentWizard` → `agentToInstall: AIAssistant?`
///  - `.showAppSettings`        → `showingSettings: Bool`
///
/// Both `ToolbarView` (in `WindowToolbarRemover`) and `RootView` receive this
/// coordinator via `@Environment(\.navigationCoordinator)` — no hidden coupling.
@Observable
@MainActor
final class AppNavigationCoordinator {

    /// The agent whose install wizard should be shown. Set to `nil` to dismiss.
    var agentToInstall: AIAssistant?

    var currentMode: AppMode = .regular

    var codeSpeakBuildRequested: Bool = false

    /// Update `currentMode` based on whether the active project is a CodeSpeak project.
    ///
    /// Called by `AppLifecycleCoordinator` whenever the active project changes.
    /// The mode is determined solely by the presence of `codespeak.json` at
    /// the project root — no user toggle.
    func syncMode(isCodeSpeak: Bool) {
        let newMode: AppMode = isCodeSpeak ? .codeSpeak : .regular
        guard newMode != currentMode else { return }
        currentMode = newMode
        if newMode == .codeSpeak {
            showingChangesPanel = false
            showingSpecPanel = false
            showingTraceabilityPanel = false
        }
    }

    /// Set to `true` to open the Settings window.
    ///
    /// `RootView` observes this and calls `openSettings()`, then resets to `false`.
    var showingSettings: Bool = false

    /// Controls the right-side git changes panel visibility.
    ///
    /// Toggled from the toolbar button (sidebar.right icon) or ⌘⇧G shortcut.
    /// `RootView` observes this to conditionally render `GitChangesPanelView`.
    var showingChangesPanel: Bool = false

    /// Controls the right-side CodeSpeak spec build panel visibility.
    ///
    /// Toggled via ⌘⇧S shortcut.
    /// `RootView` observes this to conditionally render `SpecBuildPanelView`.
    var showingSpecPanel: Bool = false

    /// Controls the right-side traceability panel visibility.
    ///
    /// Toggled via toolbar button. `RootView` observes this.
    var showingTraceabilityPanel: Bool = false

}

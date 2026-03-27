// MARK: - AppNavigationCoordinator
// Type-safe @Observable replacement for cross-component NotificationCenter events.
// macOS 14+, Swift 5.10

import Foundation
import Observation

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

    /// Set to `true` to open the Settings window.
    ///
    /// `RootView` observes this and calls `openSettings()`, then resets to `false`.
    var showingSettings: Bool = false
}

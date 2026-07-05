// MARK: - Update Servicing
// Contract for in-app auto-update (Sparkle-backed).
// macOS 14+, Swift 5.10

import Foundation

// MARK: - UpdateServicing

/// Drives in-app application updates.
///
/// Backed by Sparkle in production (see ``UpdateService``); a safe no-op
/// implementation is used for previews/tests. Kept as a narrow protocol
/// (ISP) so views depend only on the update surface, not on Sparkle types.
@MainActor
protocol UpdateServicing: AnyObject {

    /// Whether an update check can currently be initiated.
    ///
    /// `false` while a check is already in flight, or when the updater failed
    /// to start (e.g. misconfigured feed / signing key in a dev build).
    var canCheckForUpdates: Bool { get }

    /// User preference: check for updates automatically in the background.
    ///
    /// Persisted by Sparkle in `UserDefaults` (`SUEnableAutomaticChecks`).
    var automaticallyChecksForUpdates: Bool { get set }

    /// Marketing version of the running app (`CFBundleShortVersionString`).
    var currentVersion: String { get }

    /// Begin a user-initiated update check. Shows Sparkle UI on its own.
    func checkForUpdates()
}

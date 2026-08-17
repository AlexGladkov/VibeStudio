// MARK: - DisabledUpdateService
// Lightweight update-service implementation for hosted XCTest launches.
// macOS 14+, Swift 5.10

import Foundation

/// No-op ``UpdateServicing`` implementation used while hosted XCTest attaches.
///
/// Avoids constructing Sparkle's updater during test-host startup. The menu and
/// settings UI can still read update state, but all update checks remain
/// disabled until the app is launched normally.
@MainActor
final class DisabledUpdateService: UpdateServicing {
    var canCheckForUpdates: Bool { false }
    var automaticallyChecksForUpdates: Bool = false
    var currentVersion: String { Bundle.main.appShortVersion }

    func checkForUpdates() {}
}

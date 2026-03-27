// MARK: - GitStatusPolling
// Protocol for periodic git status polling.
// macOS 14+, Swift 5.10

import Foundation
import Observation

/// Abstraction over periodic git status polling.
///
/// Implementations observe a git repository and periodically refresh
/// the ``GitStatus``. Used by the AppDelegate to drive the git panel
/// and by views to display current repository state.
///
/// Inherits `Observable` so that SwiftUI views subscribed via `@Environment`
/// or direct access receive automatic re-renders when `status` changes.
@MainActor
protocol GitStatusPolling: AnyObject, Observable {

    /// Current git status for the observed repository.
    var status: GitStatus { get }

    /// Whether a poll cycle is currently in progress.
    var isPolling: Bool { get }

    /// Last error encountered during polling (`nil` if last poll succeeded).
    var lastError: Error? { get }

    /// Start polling for a specific repository.
    ///
    /// Cancels any existing polling task and starts a new one.
    ///
    /// - Parameters:
    ///   - repository: Root path of the git repository.
    ///   - isActive: Whether this is the active (foreground) project.
    func startPolling(for repository: URL, isActive: Bool)

    /// Stop polling.
    func stopPolling()

    /// Trigger an immediate refresh (e.g., on file system change).
    func refreshNow()
}

// MARK: - GitStatusPoller
// Periodic polling of git status with exponential backoff on errors.
// Integrates with FileSystemWatcher for immediate refresh.
// macOS 14+, Swift 5.10

import Foundation
import Observation
import os

/// Periodically polls git status for the active project.
///
/// Polling intervals:
/// - Active project: 3 seconds
/// - Background projects: 30 seconds
/// - On file system change: immediate refresh
/// - On error: exponential backoff (3 -> 6 -> 12 -> 30 seconds)
@Observable
@MainActor
final class GitStatusPoller: GitStatusPolling {

    // MARK: - Observable State

    /// Current git status for the active project.
    private(set) var status: GitStatus = .empty

    /// Whether a poll is currently in progress.
    private(set) var isPolling: Bool = false

    /// Last error encountered during polling (nil if last poll succeeded).
    private(set) var lastError: Error?

    // MARK: - Configuration

    /// Base polling interval for the active project (seconds).
    private let activeInterval: TimeInterval = 3

    /// Polling interval for background projects (seconds).
    private let backgroundInterval: TimeInterval = 30

    /// Maximum backoff interval on errors (seconds).
    private let maxBackoffInterval: TimeInterval = 30

    // MARK: - Private State

    // ISP: only status(at:) / aheadBehind(at:) are used — the narrowest
    // sub-protocol that covers both is `GitStatusQuerying`.
    private let gitService: any GitStatusQuerying

    /// P1-3: Polling and refresh tasks behind a lock so `deinit` (nonisolated)
    /// can cancel without racing `@MainActor` assignments.
    private struct PollingState: @unchecked Sendable {
        var pollingTask: Task<Void, Never>?
        var refreshTask: Task<Void, Never>?
    }
    private let pollingLock = OSAllocatedUnfairLock(initialState: PollingState())

    nonisolated private var pollingTask: Task<Void, Never>? {
        get { pollingLock.withLock { $0.pollingTask } }
        set { pollingLock.withLock { $0.pollingTask = newValue } }
    }
    nonisolated private var refreshTask: Task<Void, Never>? {
        get { pollingLock.withLock { $0.refreshTask } }
        set { pollingLock.withLock { $0.refreshTask = newValue } }
    }

    private var currentRepository: URL?
    private var consecutiveErrors: Int = 0
    /// P2-6: Set to `true` when a `refreshNow()` call arrives while `isPolling`
    /// is active. The in-progress poll checks this flag on completion and
    /// schedules one more cycle, ensuring FSEvents bursts are never silently
    /// dropped (previously the `guard !isPolling else { return }` in `poll()`
    /// caused stale git-status after a burst of file-system events).
    private var pendingRefresh: Bool = false

    // MARK: - Init

    init(gitService: any GitStatusQuerying) {
        self.gitService = gitService
    }

    deinit {
        pollingTask?.cancel()
        refreshTask?.cancel()
    }

    // MARK: - Public API

    /// Start polling for a specific repository.
    ///
    /// Cancels any existing polling task and starts a new one.
    ///
    /// - Parameters:
    ///   - repository: Root path of the git repository.
    ///   - isActive: Whether this is the active (foreground) project.
    func startPolling(for repository: URL, isActive: Bool = true) {
        stopPolling()
        currentRepository = repository
        consecutiveErrors = 0

        pollingTask = Task { @MainActor [weak self] in
            guard let self else { return }

            while !Task.isCancelled {
                await self.poll()

                let interval = self.effectiveInterval(isActive: isActive)
                try? await Task.sleep(for: .seconds(interval))
            }
        }
    }

    /// Stop polling.
    func stopPolling() {
        pollingTask?.cancel()
        pollingTask = nil
    }

    /// Trigger an immediate refresh (e.g., on file system change).
    ///
    /// **P2-6 fix:** If a poll cycle is already in progress (`isPolling`),
    /// the request is no longer silently dropped. Instead, `pendingRefresh`
    /// is set so the active `poll()` schedules one more cycle on completion.
    /// This prevents a stale git-status after rapid FSEvents bursts where
    /// the last event would have landed inside an active poll window.
    func refreshNow() {
        if isPolling {
            // Poll in progress — record the pending request. poll() will
            // consume it and re-run once the current cycle completes.
            pendingRefresh = true
            return
        }
        refreshTask?.cancel()
        refreshTask = Task { @MainActor [weak self] in
            await self?.poll()
        }
    }

    // MARK: - Private

    /// Execute a single poll cycle.
    ///
    /// **P2-6:** After completing, checks `pendingRefresh`. If set, runs one
    /// more cycle immediately to honour any `refreshNow()` calls that arrived
    /// while this cycle was in progress.
    private func poll() async {
        guard let repository = currentRepository else { return }
        guard !isPolling else { return }

        isPolling = true
        defer { isPolling = false }

        do {
            let newStatus = try await gitService.status(at: repository)

            // Also fetch ahead/behind if we have a branch.
            var ahead = newStatus.aheadCount
            var behind = newStatus.behindCount
            if !newStatus.branch.isEmpty && ahead == 0 && behind == 0 {
                if let counts = try? await gitService.aheadBehind(at: repository) {
                    ahead = counts.ahead
                    behind = counts.behind
                }
            }

            self.status = GitStatus(
                branch: newStatus.branch,
                aheadCount: ahead,
                behindCount: behind,
                stagedFiles: newStatus.stagedFiles,
                unstagedFiles: newStatus.unstagedFiles,
                untrackedFiles: newStatus.untrackedFiles
            )
            self.lastError = nil
            self.consecutiveErrors = 0
        } catch {
            self.lastError = error
            self.consecutiveErrors += 1
        }

        // P2-6: Consume any pending refresh request that arrived during this
        // cycle and schedule one more poll if needed.
        if pendingRefresh {
            pendingRefresh = false
            refreshTask?.cancel()
            refreshTask = Task { @MainActor [weak self] in
                await self?.poll()
            }
        }
    }

    /// Calculate the effective polling interval for the current error state.
    ///
    /// Delegates to the pure ``effectiveInterval(isActive:consecutiveErrors:)``
    /// so the production call site and the unit tests exercise the exact same
    /// backoff formula (no divergent copy).
    ///
    /// `internal` (not `private`) so tests can drive it directly.
    func effectiveInterval(isActive: Bool) -> TimeInterval {
        effectiveInterval(isActive: isActive, consecutiveErrors: consecutiveErrors)
    }

    /// Pure exponential-backoff formula — the single source of truth.
    ///
    /// - Parameters:
    ///   - isActive: `true` for the foreground project (3s base), `false` for
    ///     background projects (30s base).
    ///   - consecutiveErrors: Number of consecutive failed polls. `0` means the
    ///     base interval; each error doubles it (capped at 4 doublings and at
    ///     ``maxBackoffInterval``).
    func effectiveInterval(isActive: Bool, consecutiveErrors: Int) -> TimeInterval {
        let base = isActive ? activeInterval : backgroundInterval

        if consecutiveErrors > 0 {
            let backoff = base * pow(2.0, Double(min(consecutiveErrors, 4)))
            return min(backoff, maxBackoffInterval)
        }

        return base
    }
}

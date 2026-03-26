// MARK: - GitStatusPoller
// Periodic polling of git status with exponential backoff on errors.
// Integrates with FileSystemWatcher for immediate refresh.
// macOS 14+, Swift 5.10

import Foundation
import Observation

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

    private let gitService: GitService
    // nonisolated(unsafe): deinit is nonisolated and must cancel this task.
    // Safe because deinit only runs when no other references exist.
    nonisolated(unsafe) private var pollingTask: Task<Void, Never>?
    private var currentRepository: URL?
    private var consecutiveErrors: Int = 0

    // MARK: - Init

    init(gitService: GitService) {
        self.gitService = gitService
    }

    deinit {
        pollingTask?.cancel()
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
    func refreshNow() {
        Task { @MainActor [weak self] in
            await self?.poll()
        }
    }

    // MARK: - Private

    /// Execute a single poll cycle.
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
    }

    /// Calculate effective polling interval with exponential backoff.
    private func effectiveInterval(isActive: Bool) -> TimeInterval {
        let base = isActive ? activeInterval : backgroundInterval

        if consecutiveErrors > 0 {
            let backoff = base * pow(2.0, Double(min(consecutiveErrors, 4)))
            return min(backoff, maxBackoffInterval)
        }

        return base
    }
}

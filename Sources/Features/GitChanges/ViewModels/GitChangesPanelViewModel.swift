// MARK: - GitChangesPanelViewModel
// View model for the right-side git changes panel.
// macOS 14+, Swift 5.10

import Foundation
import OSLog
import Observation

/// View model for the right-side git changes panel.
///
/// Handles file staging and unstaging. Diff loading is delegated to
/// ``FileDiffSheetView`` which is opened on double-click.
@Observable
@MainActor
final class GitChangesPanelViewModel {

    // MARK: - LoadingState

    /// Loading lifecycle for the per-file diff stats request.
    ///
    /// Drives the panel's empty / loading / error states so a git failure
    /// (e.g. corrupted index) does not silently render as "Working tree clean".
    enum LoadingState: Equatable {
        case loading
        case ready
        case error(String)
    }

    // MARK: - State

    /// Whether a stage/unstage action is currently in progress.
    private(set) var isPerformingAction: Bool = false

    /// Per-file line stats (additions/deletions), keyed by relative path.
    private(set) var fileStats: [String: GitDiffStat] = [:]

    /// Loading lifecycle for `loadStats()`.
    private(set) var loadingState: LoadingState = .loading

    // MARK: - Dependencies

    private let gitService: any GitServicing
    private let projectManager: any ProjectManaging

    // MARK: - Init

    init(gitService: any GitServicing, projectManager: any ProjectManaging) {
        self.gitService = gitService
        self.projectManager = projectManager
    }

    // MARK: - Stats

    /// Load per-file line stats from `git diff --numstat`.
    func loadStats() async {
        guard let project = projectManager.activeProject else {
            fileStats = [:]
            loadingState = .ready
            return
        }
        loadingState = .loading
        do {
            fileStats = try await gitService.diffStats(at: project.path)
            loadingState = .ready
        } catch {
            fileStats = [:]
            loadingState = .error(error.localizedDescription)
            Logger.git.error(
                "GitChangesPanelVM: loadStats failed: \(error.localizedDescription, privacy: .public)"
            )
        }
    }

    // MARK: - Actions

    /// Stage the given file.
    func stageFile(_ file: GitFile) {
        Task { [weak self] in
            guard let self else { return }
            guard let project = projectManager.activeProject else { return }
            isPerformingAction = true
            do {
                try await gitService.stage(files: [file.path], at: project.path)
            } catch {
                Logger.git.error(
                    "GitChangesPanelVM: stage error for \(file.path, privacy: .public): \(error.localizedDescription, privacy: .public)"
                )
            }
            isPerformingAction = false
        }
    }

    /// Unstage the given file.
    func unstageFile(_ file: GitFile) {
        Task { [weak self] in
            guard let self else { return }
            guard let project = projectManager.activeProject else { return }
            isPerformingAction = true
            do {
                try await gitService.unstage(files: [file.path], at: project.path)
            } catch {
                Logger.git.error(
                    "GitChangesPanelVM: unstage error for \(file.path, privacy: .public): \(error.localizedDescription, privacy: .public)"
                )
            }
            isPerformingAction = false
        }
    }
}

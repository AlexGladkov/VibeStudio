// MARK: - CodeSpeakService
// Per-project CodeSpeak config detection and stats cache.
// macOS 14+, Swift 5.10

import Foundation
import Observation
import OSLog

// MARK: - CodeSpeakService

/// Observable service tracking CodeSpeak configuration and build stats per project.
///
/// Stores a per-project cache of:
/// - Whether `codespeak.json` exists at the project root.
/// - Latest build stats (passing/total spec counts).
///
/// Views and ViewModels read this service via `@Environment(\.codeSpeak)`.
/// Stats are written by `SpecBuildPanelViewModel` after each build run.
@Observable
@MainActor
final class CodeSpeakService {

    // MARK: - Published State

    /// Keyed by project UUID: `true` if `codespeak.json` exists at the project root.
    private(set) var projectHasConfig: [UUID: Bool] = [:]

    /// Latest build stats per project. Nil until the first build completes.
    private(set) var projectStats: [UUID: SpecStats] = [:]

    // MARK: - Config Detection

    /// Check whether `codespeak.json` exists at the given project's root.
    ///
    /// Called by `AppLifecycleCoordinator` when the active project changes.
    /// Result is cached in `projectHasConfig[project.id]`.
    func checkConfig(for project: Project) {
        let configPath = project.path.appending(path: "codespeak.json")
        let exists = FileManager.default.fileExists(atPath: configPath.path)
        projectHasConfig[project.id] = exists
        Logger.services.info(
            "CodeSpeak config for \(project.name, privacy: .public): \(exists ? "found" : "not found", privacy: .public)"
        )
    }

    /// Update build stats for a project after a `codespeak build` run completes.
    ///
    /// Called by `SpecBuildPanelViewModel` when parsing build output.
    func updateStats(_ stats: SpecStats, for projectId: UUID) {
        projectStats[projectId] = stats
    }

    /// Returns `true` if the project has a `codespeak.json` at its root.
    ///
    /// Convenience wrapper around `projectHasConfig[projectId]`.
    func isCodeSpeakProject(_ projectId: UUID) -> Bool {
        projectHasConfig[projectId] == true
    }

    /// Remove all cached data for a project (called when project is removed).
    func clearCache(for projectId: UUID) {
        projectHasConfig.removeValue(forKey: projectId)
        projectStats.removeValue(forKey: projectId)
    }
}

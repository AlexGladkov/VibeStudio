// MARK: - SpecsViewModel
// Loads and tracks spec files in the project's spec/ directory.
// macOS 14+, Swift 5.10

import Foundation
import Observation
import OSLog

/// ViewModel for the SPECS sidebar section.
///
/// Scans `<projectRoot>/spec/*.cs.md` for spec files and maintains
/// their current build status.
@Observable
@MainActor
final class SpecsViewModel {

    // MARK: - State

    /// All spec files found in the current project's `spec/` directory.
    private(set) var specFiles: [SpecFile] = []

    /// Aggregate build stats from the last build run (nil if never built).
    private(set) var stats: SpecStats?

    /// True while a directory scan is in progress.
    private(set) var isLoading = false

    // MARK: - Helpers

    private let fileManager = FileManager.default

    // MARK: - Load

    /// Scan `<projectRoot>/spec/*.cs.md` and populate `specFiles`.
    func loadSpecs(at projectRoot: URL) async {
        isLoading = true
        defer { isLoading = false }

        let specDir = projectRoot.appending(path: "spec")
        guard fileManager.fileExists(atPath: specDir.path) else {
            specFiles = []
            return
        }

        do {
            let contents = try fileManager.contentsOfDirectory(
                at: specDir,
                includingPropertiesForKeys: [.isRegularFileKey],
                options: [.skipsHiddenFiles]
            )
            let csFiles = contents
                .filter { $0.pathExtension == "md" && $0.deletingPathExtension().pathExtension == "cs" }
                .sorted { $0.lastPathComponent < $1.lastPathComponent }
            specFiles = csFiles.map { SpecFile(url: $0) }
        } catch {
            Logger.services.error("SpecsViewModel: failed to scan spec dir: \(error.localizedDescription, privacy: .public)")
            specFiles = []
        }
    }

    /// Reload the spec list (e.g. after a wizard creates a new spec).
    func refresh(at projectRoot: URL) async {
        await loadSpecs(at: projectRoot)
    }

    /// Update the build status of individual spec files from latest `SpecStats`.
    ///
    /// Called by `SpecBuildPanelViewModel` after parsing build output.
    func applyStats(_ newStats: SpecStats) {
        stats = newStats
    }

    /// Update an individual spec file's status from build output.
    func updateSpecStatus(name: String, status: SpecStatus, pass: Int, total: Int) {
        if let idx = specFiles.firstIndex(where: { $0.name == name }) {
            specFiles[idx].status = status
            specFiles[idx].passCount = pass
            specFiles[idx].totalCount = total
        }
    }
}

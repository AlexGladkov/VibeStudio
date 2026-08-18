// MARK: - FileTreeViewModel
// Manages file tree state and file system observation.
// macOS 14+, Swift 5.10

import Foundation
import Observation
import os

/// Manages the file tree state for a project directory.
///
/// Handles:
/// - Async tree building via FileTreeBuilder
/// - File system event observation for auto-refresh
/// - Debounced rebuild to avoid thrashing on rapid file changes
@Observable
@MainActor
final class FileTreeViewModel {

    // MARK: - Observable State

    private(set) var tree: [FileTreeNode] = []
    /// Set of expanded directory paths. Writable so SwiftUI Binding can mutate it.
    var expandedDirs: Set<String> = []

    // MARK: - Private State

    /// P1-3: Protected by a lock so `deinit` (nonisolated) can safely cancel
    /// without racing against a `@MainActor` assignment to the slot.
    private let rebuildTaskLock = OSAllocatedUnfairLock<Task<Void, Never>?>(initialState: nil)
    nonisolated private var rebuildTask: Task<Void, Never>? {
        get { rebuildTaskLock.withLock { $0 } }
        set { rebuildTaskLock.withLock { $0 = newValue } }
    }
    private let debounceMilliseconds: Int = 500

    // MARK: - Init

    init() {}

    deinit {
        rebuildTask?.cancel()
    }

    // MARK: - Public API

    /// Toggle expand/collapse for a directory path.
    func toggleDirectory(_ path: String) {
        if expandedDirs.contains(path) {
            expandedDirs.remove(path)
        } else {
            expandedDirs.insert(path)
        }
    }

    /// Expand a directory.
    func expandDirectory(_ path: String) {
        expandedDirs.insert(path)
    }

    /// Trigger an immediate tree rebuild.
    func rebuildTree(at projectPath: URL) {
        rebuildTask?.cancel()
        rebuildTask = Task { [weak self] in
            let nodes = await Task.detached(priority: .utility) {
                guard !Task.isCancelled else { return [FileTreeNode]() }
                return FileTreeBuilder.buildTree(at: projectPath)
            }.value
            guard !Task.isCancelled else { return }
            self?.tree = nodes
        }
    }

    /// Cancel any pending rebuild task.
    func cancelRebuild() {
        rebuildTask?.cancel()
    }

    /// Start observing file system events and rebuild on changes.
    ///
    /// This method is designed to be called from a SwiftUI `.task(id:)` modifier.
    /// It runs indefinitely until cancelled.
    func observeFileSystemEvents(
        projectPath: URL,
        fileSystemWatcher: any FileSystemWatching
    ) async {
        rebuildTree(at: projectPath)
        let projectPrefix = projectPath.path
        var debounceTask: Task<Void, Never>?
        for await event in fileSystemWatcher.events {
            guard event.path.path.hasPrefix(projectPrefix) else { continue }
            debounceTask?.cancel()
            debounceTask = Task { @MainActor [weak self] in
                guard let self else { return }
                try? await Task.sleep(for: .milliseconds(self.debounceMilliseconds))
                guard !Task.isCancelled else { return }
                self.rebuildTree(at: projectPath)
            }
        }
    }
}

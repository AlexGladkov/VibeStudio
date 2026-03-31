// MARK: - ProjectStore
// JSON-backed project list persistence.
// macOS 14+, Swift 5.10

import Foundation
import Observation
import OSLog

/// Persistent store for the project list.
///
/// Projects are stored in `~/Library/Application Support/VibeStudio/projects.json`.
/// The directory is created with permissions 0o700 (owner-only access).
///
/// Implements ``ProjectManaging`` with `@Observable` for SwiftUI integration.
@Observable
@MainActor
final class ProjectStore: ProjectManaging {

    // MARK: - Constants

    /// Maximum number of projects allowed (protection against OOM).
    private let maxProjects = 32

    /// Maximum number of recent projects to track.
    private let maxRecent = 10

    // MARK: - Observable State

    private(set) var projects: [Project] = []

    /// History of all projects ever added, persisted independently of `projects`.
    /// Used to populate the Welcome screen "Recent" list after projects are removed.
    var recentHistory: [Project] = []

    var activeProjectId: UUID? {
        didSet {
            // Update lastOpened timestamp for the newly active project.
            if let id = activeProjectId,
               let index = projects.firstIndex(where: { $0.id == id }) {
                projects[index].lastOpened = .now
                // Mirror into recent history.
                updateRecentHistory(projects[index])
                do {
                    try save()
                    try saveRecents()
                } catch {
                    Logger.persistence.error("Failed to save after activeProjectId change: \(error)")
                }
            }
        }
    }

    /// Projects from history that are NOT currently open in the sidebar.
    /// Used by WelcomeView.
    var recentProjects: [Project] {
        let currentIds = Set(projects.map { $0.id })
        return recentHistory.filter { !currentIds.contains($0.id) }
    }

    // MARK: - Private State

    /// Lookup dictionary for O(1) access by ID.
    private var projectIndex: [UUID: Int] = [:]

    /// Persistence file path.
    private let storageURL: URL

    /// Persistence file path for recent history.
    private let recentsURL: URL

    // MARK: - Init

    /// Creates a new `ProjectStore`.
    ///
    /// - Parameter storageDirectory: Override directory for persistence file.
    ///   Pass a temporary directory in unit tests to avoid touching the real
    ///   Application Support folder. When `nil` (default), uses the standard
    ///   `~/Library/Application Support/VibeStudio/` path.
    init(storageDirectory: URL? = nil) {
        let baseDir: URL
        if let storageDirectory {
            baseDir = storageDirectory
        } else if let appDir = try? PathConstants.appSupportDirectory {
            baseDir = appDir
        } else {
            Logger.persistence.error("Application Support directory not found, using temp directory")
            baseDir = FileManager.default.temporaryDirectory.appendingPathComponent("VibeStudio")
        }

        // Ensure directory exists (important for test directories).
        try? FileManager.default.createDirectory(
            at: baseDir,
            withIntermediateDirectories: true,
            attributes: [.posixPermissions: 0o700]
        )

        self.storageURL = baseDir.appendingPathComponent("projects.json")
        self.recentsURL = baseDir.appendingPathComponent("recents.json")
    }

    // MARK: - ProjectManaging: CRUD

    @discardableResult
    func addProject(at path: URL) throws -> Project {
        // Validate path exists and is a directory.
        var isDir: ObjCBool = false
        guard FileManager.default.fileExists(atPath: path.path, isDirectory: &isDir),
              isDir.boolValue else {
            throw ProjectManagerError.invalidPath(path)
        }

        // Resolve symlinks and normalize path to prevent traversal attacks.
        let standardizedPath = path.standardizedFileURL.resolvingSymlinksInPath()
        guard !PathConstants.forbiddenRootPaths.contains(standardizedPath.path) else {
            throw ProjectManagerError.invalidPath(path)
        }

        // Check for duplicate.
        if let existing = project(at: path) {
            throw ProjectManagerError.duplicate(existingId: existing.id, path: path)
        }

        // Enforce project limit.
        guard projects.count < maxProjects else {
            throw ProjectManagerError.projectLimitReached(max: maxProjects)
        }

        let project = Project(
            name: path.lastPathComponent,
            path: path
        )

        projects.append(project)
        rebuildIndex()
        updateRecentHistory(project)
        try save()
        try saveRecents()

        return project
    }

    func removeProject(_ id: UUID) throws {
        guard let index = projects.firstIndex(where: { $0.id == id }) else {
            throw ProjectManagerError.notFound(id)
        }

        projects.remove(at: index)
        rebuildIndex()

        // If removed project was active, clear selection.
        if activeProjectId == id {
            activeProjectId = projects.first?.id
        }

        try save()
    }

    func updateProject(_ id: UUID, _ mutate: (inout Project) -> Void) throws {
        guard let index = projects.firstIndex(where: { $0.id == id }) else {
            throw ProjectManagerError.notFound(id)
        }

        mutate(&projects[index])
        try save()
    }

    func moveProjects(from indices: IndexSet, to destination: Int) {
        projects.move(fromOffsets: indices, toOffset: destination)
        rebuildIndex()
        try? save()
    }

    // MARK: - ProjectManaging: Lookup

    func project(for id: UUID) -> Project? {
        guard let index = projectIndex[id], index < projects.count else {
            return nil
        }
        return projects[index]
    }

    func project(at path: URL) -> Project? {
        let standardized = path.standardizedFileURL
        return projects.first { $0.path.standardizedFileURL == standardized }
    }

    // MARK: - ProjectManaging: Persistence

    func load() throws {
        guard FileManager.default.fileExists(atPath: storageURL.path) else {
            loadRecents()
            return // No saved data yet.
        }

        do {
            let data = try Data(contentsOf: storageURL)
            let decoded = try JSONDecoder().decode([Project].self, from: data)
            self.projects = decoded
            rebuildIndex()
        } catch {
            throw ProjectManagerError.persistenceFailed(underlying: error)
        }

        loadRecents()
    }

    func save() throws {
        do {
            let encoder = JSONEncoder()
            encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
            let data = try encoder.encode(projects)
            try data.write(to: storageURL, options: .atomic)
        } catch {
            throw ProjectManagerError.persistenceFailed(underlying: error)
        }
    }

    // MARK: - Private

    /// Rebuild the O(1) lookup index after mutations.
    private func rebuildIndex() {
        projectIndex = Dictionary(
            uniqueKeysWithValues: projects.enumerated().map { ($1.id, $0) }
        )
    }

    /// Insert or update a project in recentHistory (capped at maxRecent).
    private func updateRecentHistory(_ project: Project) {
        if let idx = recentHistory.firstIndex(where: { $0.path == project.path }) {
            recentHistory[idx] = project
        } else {
            recentHistory.insert(project, at: 0)
        }
        // Keep sorted by lastOpened desc, capped at maxRecent.
        recentHistory = Array(
            recentHistory
                .sorted { $0.lastOpened > $1.lastOpened }
                .prefix(maxRecent)
        )
    }

    private func loadRecents() {
        guard FileManager.default.fileExists(atPath: recentsURL.path),
              let data = try? Data(contentsOf: recentsURL),
              let decoded = try? JSONDecoder().decode([Project].self, from: data) else { return }
        recentHistory = decoded
    }

    private func saveRecents() throws {
        do {
            let encoder = JSONEncoder()
            encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
            let data = try encoder.encode(recentHistory)
            try data.write(to: recentsURL, options: .atomic)
        } catch {
            throw ProjectManagerError.persistenceFailed(underlying: error)
        }
    }
}

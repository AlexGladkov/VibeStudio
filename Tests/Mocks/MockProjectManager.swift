import Foundation
@testable import VibeStudio

/// Mock implementation of ``ProjectManaging`` for unit tests.
///
/// Operates entirely in-memory -- no disk persistence.
/// Conforms to `@Observable` and `@MainActor` as required by the protocol.
@Observable
@MainActor
final class MockProjectManager: ProjectManaging {

    // MARK: - Observable State

    private(set) var projects: [Project] = []
    var activeProjectId: UUID?

    var recentProjects: [Project] {
        Array(projects.sorted { $0.lastOpened > $1.lastOpened }.prefix(10))
    }

    // MARK: - Call Tracking

    var addProjectCallCount = 0
    var removeProjectCallCount = 0
    var updateProjectCallCount = 0
    var loadCallCount = 0
    var saveCallCount = 0

    // MARK: - CRUD

    @discardableResult
    func addProject(at path: URL) throws -> Project {
        addProjectCallCount += 1
        let project = Project(name: path.lastPathComponent, path: path)
        projects.append(project)
        return project
    }

    func removeProject(_ id: UUID) throws {
        removeProjectCallCount += 1
        guard let index = projects.firstIndex(where: { $0.id == id }) else {
            throw ProjectManagerError.notFound(id)
        }
        projects.remove(at: index)
        if activeProjectId == id {
            activeProjectId = projects.first?.id
        }
    }

    func updateProject(_ id: UUID, _ mutate: (inout Project) -> Void) throws {
        updateProjectCallCount += 1
        guard let index = projects.firstIndex(where: { $0.id == id }) else {
            throw ProjectManagerError.notFound(id)
        }
        mutate(&projects[index])
    }

    func moveProjects(from indices: IndexSet, to destination: Int) {
        projects.move(fromOffsets: indices, toOffset: destination)
    }

    // MARK: - Lookup

    func project(for id: UUID) -> Project? {
        projects.first { $0.id == id }
    }

    func project(at path: URL) -> Project? {
        let standardized = path.standardizedFileURL
        return projects.first { $0.path.standardizedFileURL == standardized }
    }

    // MARK: - Lifecycle

    func load() throws {
        loadCallCount += 1
        // No-op in mock -- projects are set directly in tests.
    }

    func save() throws {
        saveCallCount += 1
        // No-op in mock -- in-memory only.
    }
}

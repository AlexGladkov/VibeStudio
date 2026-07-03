import XCTest
@testable import VibeStudio

/// Unit tests for ``SaveSessionUseCase``. Verifies the `resolvedActiveId`
/// logic (a FreeTab UUID that is not a real project must not be persisted) and
/// the projects → snapshot mapping. Persistence is captured by
/// ``MockSessionPersisting``.
@MainActor
final class SaveSessionUseCaseTests: XCTestCase {

    private func makeSUT(
        projects: MockProjectManager,
        terminals: MockTerminalSessionManager,
        persistence: MockSessionPersisting
    ) -> SaveSessionUseCase {
        SaveSessionUseCase(
            projectManager: projects,
            terminalManager: terminals,
            sessionPersistence: persistence
        )
    }

    // MARK: - resolvedActiveId

    func testFreeTabActiveIdIsNotPersisted() async throws {
        let projects = MockProjectManager()
        _ = try projects.addProject(at: URL(fileURLWithPath: "/tmp/repo"))
        // Active id points at a FreeTab (not a real project).
        projects.activeProjectId = UUID()

        let terminals = MockTerminalSessionManager()
        let persistence = MockSessionPersisting()
        let sut = makeSUT(projects: projects, terminals: terminals, persistence: persistence)

        await sut.execute()

        let saved = await persistence.savedSnapshot
        XCTAssertNotNil(saved)
        XCTAssertNil(saved?.activeProjectId, "a FreeTab (non-project) active id must be dropped")
    }

    func testRealActiveIdIsPersisted() async throws {
        let projects = MockProjectManager()
        let project = try projects.addProject(at: URL(fileURLWithPath: "/tmp/repo"))
        projects.activeProjectId = project.id

        let terminals = MockTerminalSessionManager()
        let persistence = MockSessionPersisting()
        let sut = makeSUT(projects: projects, terminals: terminals, persistence: persistence)

        await sut.execute()

        let saved = await persistence.savedSnapshot
        XCTAssertEqual(saved?.activeProjectId, project.id)
    }

    // MARK: - Projects → snapshot mapping

    func testProjectsMapToSnapshotWithTerminalLayouts() async throws {
        let projects = MockProjectManager()
        let projectA = try projects.addProject(at: URL(fileURLWithPath: "/tmp/a"))
        let projectB = try projects.addProject(at: URL(fileURLWithPath: "/tmp/b"))

        let terminals = MockTerminalSessionManager()
        // Two sessions for A, none for B.
        terminals.sessionsByProject[projectA.id] = [
            TerminalSession(projectId: projectA.id, title: "one"),
            TerminalSession(projectId: projectA.id, title: "two")
        ]

        let persistence = MockSessionPersisting()
        let sut = makeSUT(projects: projects, terminals: terminals, persistence: persistence)

        await sut.execute()

        let saved = await persistence.savedSnapshot
        XCTAssertEqual(saved?.version, persistence.currentSnapshotVersion)
        XCTAssertEqual(saved?.projectSessions.count, 2)

        let sessionA = saved?.projectSessions.first { $0.projectId == projectA.id }
        let sessionB = saved?.projectSessions.first { $0.projectId == projectB.id }
        XCTAssertEqual(sessionA?.terminalLayouts.count, 2)
        XCTAssertEqual(sessionB?.terminalLayouts.count, 0)
        // Layout working directory is derived from the project path.
        XCTAssertEqual(sessionA?.terminalLayouts.first?.workingDirectory, projectA.path)
    }
}

import XCTest
@testable import VibeStudio

/// Unit tests for ``ActivateFirstProjectUseCase``. Verifies the no-op when a
/// project is already active, the duplicate-session guard when the first
/// project already has terminals, and the happy path that activates the first
/// project and spawns a session. In-memory mocks throughout.
@MainActor
final class ActivateFirstProjectUseCaseTests: XCTestCase {

    private func makeSUT(
        projects: MockProjectManager,
        terminals: MockTerminalSessionManager
    ) -> ActivateFirstProjectUseCase {
        ActivateFirstProjectUseCase(projectManager: projects, terminalManager: terminals)
    }

    // MARK: - Already active → no-op

    func testAlreadyActiveProjectIsNoOp() throws {
        let projects = MockProjectManager()
        let project = try projects.addProject(at: URL(fileURLWithPath: "/tmp/repo"))
        projects.activeProjectId = project.id

        let terminals = MockTerminalSessionManager()
        let sut = makeSUT(projects: projects, terminals: terminals)

        sut.execute()

        XCTAssertEqual(projects.activeProjectId, project.id, "active selection must be preserved")
        XCTAssertEqual(terminals.createSessionCallCount, 0, "no session should be created when one is active")
    }

    // MARK: - Empty project list → no-op

    func testEmptyProjectListIsNoOp() {
        let projects = MockProjectManager()
        let terminals = MockTerminalSessionManager()
        let sut = makeSUT(projects: projects, terminals: terminals)

        sut.execute()

        XCTAssertNil(projects.activeProjectId)
        XCTAssertEqual(terminals.createSessionCallCount, 0)
    }

    // MARK: - Existing sessions → activate but do not create

    func testExistingSessionsActivateWithoutCreatingDuplicate() throws {
        let projects = MockProjectManager()
        let project = try projects.addProject(at: URL(fileURLWithPath: "/tmp/repo"))
        // activeProjectId left nil so the use case runs.

        let terminals = MockTerminalSessionManager()
        terminals.sessionsByProject[project.id] = [TerminalSession(projectId: project.id)]

        let sut = makeSUT(projects: projects, terminals: terminals)

        sut.execute()

        XCTAssertEqual(projects.activeProjectId, project.id, "first project must be activated")
        XCTAssertEqual(terminals.createSessionCallCount, 0, "an existing session must suppress creation")
    }

    // MARK: - No sessions → activate and create

    func testNoSessionsActivatesFirstAndCreatesSession() throws {
        let projects = MockProjectManager()
        let first = try projects.addProject(at: URL(fileURLWithPath: "/tmp/a"))
        _ = try projects.addProject(at: URL(fileURLWithPath: "/tmp/b"))

        let terminals = MockTerminalSessionManager()
        let sut = makeSUT(projects: projects, terminals: terminals)

        sut.execute()

        XCTAssertEqual(projects.activeProjectId, first.id, "the first project must be activated")
        XCTAssertEqual(terminals.createSessionCallCount, 1)
        XCTAssertEqual(terminals.sessions(for: first.id).count, 1)
    }
}

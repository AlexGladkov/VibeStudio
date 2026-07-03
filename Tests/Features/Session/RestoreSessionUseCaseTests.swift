import XCTest
@testable import VibeStudio

/// Unit tests for ``RestoreSessionUseCase``. Verifies the `prefix(1)` duplicate
/// guard, active-project resolution against the real project list, and the
/// nil-snapshot short-circuit. All dependencies are in-memory mocks.
@MainActor
final class RestoreSessionUseCaseTests: XCTestCase {

    // MARK: - Helpers

    private func makeSUT(
        projects: MockProjectManager,
        terminals: MockTerminalSessionManager,
        persistence: MockSessionPersisting
    ) -> RestoreSessionUseCase {
        RestoreSessionUseCase(
            projectManager: projects,
            terminalManager: terminals,
            sessionPersistence: persistence
        )
    }

    private func layout() -> TerminalLayoutSnapshot {
        TerminalLayoutSnapshot(
            sessionId: UUID(),
            title: "zsh",
            splitDirection: nil,
            workingDirectory: nil
        )
    }

    private func projectSession(id: UUID, layouts: Int) -> ProjectSessionSnapshot {
        ProjectSessionSnapshot(
            projectId: id,
            terminalLayouts: (0..<layouts).map { _ in layout() },
            scrollbackFile: nil,
            sidebarVisible: true,
            sidebarWidth: 260
        )
    }

    // MARK: - prefix(1) duplicate guard

    func testMultipleLayoutsRestoreOnlyOneSession() async throws {
        let projects = MockProjectManager()
        let project = try projects.addProject(at: URL(fileURLWithPath: "/tmp/repo"))
        let terminals = MockTerminalSessionManager()

        let snapshot = AppSessionSnapshot(
            version: 1,
            capturedAt: .now,
            activeProjectId: nil,
            projectSessions: [projectSession(id: project.id, layouts: 3)]
        )
        let persistence = MockSessionPersisting(restoreResult: snapshot)
        let sut = makeSUT(projects: projects, terminals: terminals, persistence: persistence)

        let restored = await sut.execute()

        XCTAssertTrue(restored)
        XCTAssertEqual(terminals.createSessionCallCount, 1, "prefix(1) must collapse 3 layouts into one session")
        XCTAssertEqual(terminals.sessions(for: project.id).count, 1)
    }

    // MARK: - Active project resolution

    func testActiveProjectIdForUnknownProjectIsNotApplied() async {
        let projects = MockProjectManager()
        let terminals = MockTerminalSessionManager()

        let snapshot = AppSessionSnapshot(
            version: 1,
            capturedAt: .now,
            activeProjectId: UUID(), // no matching project exists
            projectSessions: []
        )
        let persistence = MockSessionPersisting(restoreResult: snapshot)
        let sut = makeSUT(projects: projects, terminals: terminals, persistence: persistence)

        let restored = await sut.execute()

        XCTAssertTrue(restored, "a snapshot was present, so the result is true even if the active id is stale")
        XCTAssertNil(projects.activeProjectId, "an unknown active project id must not be applied")
    }

    func testActiveProjectIdForKnownProjectIsApplied() async throws {
        let projects = MockProjectManager()
        let project = try projects.addProject(at: URL(fileURLWithPath: "/tmp/repo"))
        let terminals = MockTerminalSessionManager()

        let snapshot = AppSessionSnapshot(
            version: 1,
            capturedAt: .now,
            activeProjectId: project.id,
            projectSessions: []
        )
        let persistence = MockSessionPersisting(restoreResult: snapshot)
        let sut = makeSUT(projects: projects, terminals: terminals, persistence: persistence)

        _ = await sut.execute()

        XCTAssertEqual(projects.activeProjectId, project.id)
    }

    // MARK: - Nil snapshot

    func testNilSnapshotReturnsFalse() async {
        let projects = MockProjectManager()
        let terminals = MockTerminalSessionManager()
        let persistence = MockSessionPersisting(restoreResult: nil)
        let sut = makeSUT(projects: projects, terminals: terminals, persistence: persistence)

        let restored = await sut.execute()

        XCTAssertFalse(restored)
        XCTAssertEqual(terminals.createSessionCallCount, 0)
    }
}

// MARK: - SaveSessionUseCaseTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

@MainActor
final class SaveSessionUseCaseTests: XCTestCase {

    private var projectManager: MockProjectManager!
    private var terminalManager: MockTerminalSessionManager!
    private var persistence: MockSessionPersistence!

    override func setUp() {
        super.setUp()
        projectManager = MockProjectManager()
        terminalManager = MockTerminalSessionManager()
        persistence = MockSessionPersistence()
    }

    override func tearDown() {
        projectManager = nil
        terminalManager = nil
        persistence = nil
        super.tearDown()
    }

    private func makeSUT() -> SaveSessionUseCase {
        SaveSessionUseCase(
            projectManager: projectManager,
            terminalManager: terminalManager,
            sessionPersistence: persistence
        )
    }

    func test_execute_emptyState_savesSnapshotWithNoSessions() async {
        let sut = makeSUT()
        await sut.execute()

        XCTAssertEqual(persistence.saveCallCount, 1)
        XCTAssertEqual(persistence.savedSnapshot?.projectSessions.count, 0)
        XCTAssertNil(persistence.savedSnapshot?.activeProjectId)
    }

    func test_execute_capturesProjectsAndSessions() async throws {
        let project = try projectManager.addProject(at: URL(fileURLWithPath: "/tmp/p1"))
        projectManager.activeProjectId = project.id
        _ = try terminalManager.createSession(for: project.id)

        let sut = makeSUT()
        await sut.execute()

        let snap = try XCTUnwrap(persistence.savedSnapshot)
        XCTAssertEqual(snap.activeProjectId, project.id)
        XCTAssertEqual(snap.projectSessions.count, 1)
        XCTAssertEqual(snap.projectSessions.first?.terminalLayouts.count, 1)
    }

    func test_execute_freeTabActiveId_isDroppedFromSnapshot() async throws {
        // Active ID does not match any real project — must not be persisted.
        let project = try projectManager.addProject(at: URL(fileURLWithPath: "/tmp/p1"))
        projectManager.activeProjectId = UUID() // phantom (e.g. FreeTab)

        let sut = makeSUT()
        await sut.execute()

        let snap = try XCTUnwrap(persistence.savedSnapshot)
        XCTAssertNil(snap.activeProjectId,
                     "Phantom (non-project) active IDs must be cleared during save")
        XCTAssertEqual(snap.projectSessions.count, 1)
        XCTAssertEqual(snap.projectSessions.first?.projectId, project.id)
    }

    func test_execute_persistenceThrows_isSwallowed() async {
        struct Boom: Error {}
        persistence.saveErrorToThrow = Boom()
        let sut = makeSUT()
        // Should not throw.
        await sut.execute()
        XCTAssertEqual(persistence.saveCallCount, 1)
    }
}

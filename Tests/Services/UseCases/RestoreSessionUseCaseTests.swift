// MARK: - RestoreSessionUseCaseTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

@MainActor
final class RestoreSessionUseCaseTests: XCTestCase {

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

    private func makeSUT() -> RestoreSessionUseCase {
        RestoreSessionUseCase(
            projectManager: projectManager,
            terminalManager: terminalManager,
            sessionPersistence: persistence
        )
    }

    func test_execute_noSnapshot_returnsFalse() async {
        persistence.restoreResult = .success(nil)
        let sut = makeSUT()
        let restored = await sut.execute()
        XCTAssertFalse(restored)
        XCTAssertNil(projectManager.activeProjectId)
    }

    func test_execute_restoresActiveAndCreatesSinglePerProjectSession() async throws {
        let project = try projectManager.addProject(at: URL(fileURLWithPath: "/tmp/proj-a"))
        let layout1 = TerminalLayoutSnapshot(
            sessionId: UUID(), title: "t1", splitDirection: nil, workingDirectory: nil
        )
        // Two layouts — only the first should produce a session (anti-duplicate guard).
        let layout2 = TerminalLayoutSnapshot(
            sessionId: UUID(), title: "t2", splitDirection: nil, workingDirectory: nil
        )
        let projectSession = ProjectSessionSnapshot(
            projectId: project.id,
            terminalLayouts: [layout1, layout2],
            scrollbackFile: nil,
            sidebarVisible: true,
            sidebarWidth: 240
        )
        let snapshot = AppSessionSnapshot(
            version: 1, capturedAt: .now,
            activeProjectId: project.id,
            projectSessions: [projectSession]
        )
        persistence.restoreResult = .success(snapshot)

        let sut = makeSUT()
        let restored = await sut.execute()

        XCTAssertTrue(restored)
        XCTAssertEqual(projectManager.activeProjectId, project.id)
        XCTAssertEqual(terminalManager.createSessionCallCount, 1,
                       "Only one session per project should be created, even with multiple layouts")
    }

    func test_execute_unknownActiveId_isIgnored() async throws {
        // Snapshot references a project ID that does not exist in current store.
        let phantomId = UUID()
        let snapshot = AppSessionSnapshot(
            version: 1, capturedAt: .now,
            activeProjectId: phantomId,
            projectSessions: []
        )
        persistence.restoreResult = .success(snapshot)

        let sut = makeSUT()
        let restored = await sut.execute()

        XCTAssertTrue(restored)
        XCTAssertNil(projectManager.activeProjectId,
                     "Active ID is set only when the project still exists")
    }

    func test_execute_restoreThrows_returnsFalse() async {
        struct Boom: Error {}
        persistence.restoreResult = .failure(Boom())
        let sut = makeSUT()
        let restored = await sut.execute()
        XCTAssertFalse(restored)
    }
}

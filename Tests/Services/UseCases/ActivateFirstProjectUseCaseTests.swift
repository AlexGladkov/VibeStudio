// MARK: - ActivateFirstProjectUseCaseTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

@MainActor
final class ActivateFirstProjectUseCaseTests: XCTestCase {

    private var projectManager: MockProjectManager!
    private var terminalManager: MockTerminalSessionManager!

    override func setUp() {
        super.setUp()
        projectManager = MockProjectManager()
        terminalManager = MockTerminalSessionManager()
    }

    override func tearDown() {
        projectManager = nil
        terminalManager = nil
        super.tearDown()
    }

    private func makeSUT() -> ActivateFirstProjectUseCase {
        ActivateFirstProjectUseCase(
            projectManager: projectManager,
            terminalManager: terminalManager
        )
    }

    func test_execute_noProjects_isNoop() {
        let sut = makeSUT()
        sut.execute()
        XCTAssertNil(projectManager.activeProjectId)
        XCTAssertEqual(terminalManager.createSessionCallCount, 0)
    }

    func test_execute_activeAlreadySet_isNoop() throws {
        let project = try projectManager.addProject(at: URL(fileURLWithPath: "/tmp/p1"))
        projectManager.activeProjectId = project.id

        let sut = makeSUT()
        sut.execute()

        XCTAssertEqual(projectManager.activeProjectId, project.id)
        XCTAssertEqual(terminalManager.createSessionCallCount, 0,
                       "Should not create a new terminal when active project is preset")
    }

    func test_execute_setsFirstProjectAndCreatesSession() throws {
        let project = try projectManager.addProject(at: URL(fileURLWithPath: "/tmp/p1"))
        _ = try projectManager.addProject(at: URL(fileURLWithPath: "/tmp/p2"))

        let sut = makeSUT()
        sut.execute()

        XCTAssertEqual(projectManager.activeProjectId, project.id)
        XCTAssertEqual(terminalManager.createSessionCallCount, 1)
    }

    func test_execute_existingSessionForFirstProject_skipsCreate() throws {
        let project = try projectManager.addProject(at: URL(fileURLWithPath: "/tmp/p1"))
        _ = try terminalManager.createSession(for: project.id)
        // reset counter to isolate the use case's create call
        terminalManager.createSessionCallCount = 0

        let sut = makeSUT()
        sut.execute()

        XCTAssertEqual(projectManager.activeProjectId, project.id)
        XCTAssertEqual(terminalManager.createSessionCallCount, 0,
                       "Should reuse the already-restored session")
    }
}

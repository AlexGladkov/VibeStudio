import XCTest
@testable import VibeStudio

@MainActor
final class TabItemViewModelTests: XCTestCase {

    private var projectManager: MockProjectManager!
    private var terminalManager: MockTerminalSessionManager!
    private var sut: TabItemViewModel!

    override func setUp() {
        super.setUp()
        projectManager = MockProjectManager()
        terminalManager = MockTerminalSessionManager()
        sut = TabItemViewModel(
            projectManager: projectManager,
            terminalManager: terminalManager
        )
    }

    override func tearDown() {
        sut = nil
        projectManager = nil
        terminalManager = nil
        super.tearDown()
    }

    // MARK: - closeProject

    func testCloseProject_killsSessionsAndRemoves() throws {
        let project = try projectManager.addProject(at: URL(fileURLWithPath: "/tmp/proj"))
        _ = try terminalManager.createSession(for: project.id)

        sut.closeProject(project.id)

        XCTAssertEqual(terminalManager.killAllSessionsCalls, [project.id])
        XCTAssertEqual(projectManager.removeProjectCallCount, 1)
        XCTAssertNil(projectManager.project(for: project.id))
    }

    func testCloseProject_nonexistentId_doesNotThrow() {
        let fakeId = UUID()
        // Should not crash -- removeProject failure is silently caught.
        sut.closeProject(fakeId)
        XCTAssertEqual(terminalManager.killAllSessionsCalls, [fakeId])
    }

    func testCloseProject_activeProject_switchesToAnother() throws {
        let project1 = try projectManager.addProject(at: URL(fileURLWithPath: "/tmp/proj1"))
        let project2 = try projectManager.addProject(at: URL(fileURLWithPath: "/tmp/proj2"))
        projectManager.activeProjectId = project1.id

        sut.closeProject(project1.id)

        // MockProjectManager sets activeProjectId to first remaining project.
        XCTAssertEqual(projectManager.activeProjectId, project2.id)
    }
}

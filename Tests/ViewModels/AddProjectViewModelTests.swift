// MARK: - AddProjectViewModelTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

@MainActor
final class AddProjectViewModelTests: XCTestCase {

    func test_openRecentProject_happyPath_setsActiveProjectAndDismisses() {
        let pm = MockProjectManager()
        let sut = AddProjectViewModel(projectManager: pm)
        let project = Project(name: "demo", path: URL(fileURLWithPath: "/tmp/demo"))

        let shouldDismiss = sut.openRecentProject(project)
        XCTAssertTrue(shouldDismiss)
        XCTAssertNotNil(pm.activeProjectId)
        XCTAssertNil(sut.openError)
    }

    func test_openProject_atURL_addsAndActivates() {
        let pm = MockProjectManager()
        let sut = AddProjectViewModel(projectManager: pm)
        sut.openProject(at: URL(fileURLWithPath: "/tmp/new"))

        XCTAssertEqual(pm.addProjectCallCount, 1)
        XCTAssertNotNil(pm.activeProjectId)
    }

    func test_clearError_resetsErrorState() {
        let sut = AddProjectViewModel(projectManager: MockProjectManager())
        sut.clearError()
        XCTAssertNil(sut.openError)
    }
}

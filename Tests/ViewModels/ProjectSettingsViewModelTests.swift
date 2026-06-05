// MARK: - ProjectSettingsViewModelTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

@MainActor
final class ProjectSettingsViewModelTests: XCTestCase {

    private func makePM() throws -> (MockProjectManager, Project) {
        let pm = MockProjectManager()
        let project = try pm.addProject(at: URL(fileURLWithPath: "/tmp/p"))
        return (pm, project)
    }

    func test_save_appendsHttpsScheme_whenMissing() throws {
        let (pm, project) = try makePM()
        let sut = ProjectSettingsViewModel(projectManager: pm, project: project)
        sut.productionURL = "example.com"
        XCTAssertTrue(sut.saveSettings())
        XCTAssertEqual(pm.project(for: project.id)?.productionURL, "https://example.com")
    }

    func test_save_preservesExistingScheme() throws {
        let (pm, project) = try makePM()
        let sut = ProjectSettingsViewModel(projectManager: pm, project: project)
        sut.productionURL = "http://localhost:3000"
        _ = sut.saveSettings()
        XCTAssertEqual(pm.project(for: project.id)?.productionURL, "http://localhost:3000")
    }

    func test_save_blankString_clearsProductionURL() throws {
        let (pm, project) = try makePM()
        try pm.updateProject(project.id) { $0.productionURL = "https://old.example.com" }
        let sut = ProjectSettingsViewModel(projectManager: pm, project: project)
        sut.productionURL = "   "
        _ = sut.saveSettings()
        XCTAssertNil(pm.project(for: project.id)?.productionURL)
    }
}

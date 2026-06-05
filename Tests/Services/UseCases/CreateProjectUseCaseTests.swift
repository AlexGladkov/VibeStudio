// MARK: - CreateProjectUseCaseTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

@MainActor
final class CreateProjectUseCaseTests: XCTestCase {

    private var projectManager: MockProjectManager!
    private var tempRoot: URL!

    override func setUp() {
        super.setUp()
        projectManager = MockProjectManager()
        tempRoot = URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("CreateProjectUseCaseTests-\(UUID().uuidString)")
        try? FileManager.default.createDirectory(at: tempRoot, withIntermediateDirectories: true)
    }

    override func tearDown() {
        projectManager = nil
        try? FileManager.default.removeItem(at: tempRoot)
        tempRoot = nil
        super.tearDown()
    }

    private func makeSUT() -> CreateProjectUseCase {
        CreateProjectUseCase(projectManager: projectManager)
    }

    func test_execute_createsDirectoryRegistersAndActivates() throws {
        let sut = makeSUT()
        let project = try sut.execute(name: "MyApp", parent: tempRoot)

        let expectedURL = tempRoot.appendingPathComponent("MyApp")
        XCTAssertTrue(FileManager.default.fileExists(atPath: expectedURL.path),
                      "Project directory should exist on disk")
        XCTAssertEqual(project.name, "MyApp")
        XCTAssertEqual(projectManager.addProjectCallCount, 1)
        XCTAssertEqual(projectManager.activeProjectId, project.id)
    }

    func test_execute_existingDirectory_succeeds_intermediateAllowed() throws {
        // FileManager.createDirectory(withIntermediateDirectories: true) is
        // idempotent — the use case must therefore succeed.
        let pre = tempRoot.appendingPathComponent("Existing")
        try FileManager.default.createDirectory(at: pre, withIntermediateDirectories: true)

        let sut = makeSUT()
        XCTAssertNoThrow(try sut.execute(name: "Existing", parent: tempRoot))
    }
}

// MARK: - ProjectStoreTests
// Unit tests for ProjectStore (Phase 2).
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

@MainActor
final class ProjectStoreTests: XCTestCase {

    // MARK: - Properties

    private var tempDir: URL!
    private var store: ProjectStore!

    // MARK: - Lifecycle

    override func setUp() async throws {
        try await super.setUp()
        tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("ProjectStoreTests-\(UUID().uuidString)")
        try FileManager.default.createDirectory(
            at: tempDir,
            withIntermediateDirectories: true
        )
        store = ProjectStore(storageDirectory: tempDir)
    }

    override func tearDown() async throws {
        store = nil
        try? FileManager.default.removeItem(at: tempDir)
        tempDir = nil
        try await super.tearDown()
    }

    // MARK: - Helpers

    /// Creates a temporary directory that can be used as a fake project root.
    @discardableResult
    private func makeFakeProjectDir(named name: String = UUID().uuidString) throws -> URL {
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("FakeProjects")
            .appendingPathComponent(name)
        try FileManager.default.createDirectory(
            at: dir,
            withIntermediateDirectories: true
        )
        return dir
    }

    // MARK: - addProject

    func testAddProjectSucceeds() throws {
        let projectDir = try makeFakeProjectDir(named: "MyApp")

        let project = try store.addProject(at: projectDir)

        XCTAssertEqual(store.projects.count, 1)
        XCTAssertEqual(
            project.path.standardizedFileURL,
            projectDir.standardizedFileURL
        )
        XCTAssertEqual(project.name, "MyApp")
    }

    func testAddMultipleProjects() throws {
        let dirA = try makeFakeProjectDir(named: "Alpha")
        let dirB = try makeFakeProjectDir(named: "Beta")

        try store.addProject(at: dirA)
        try store.addProject(at: dirB)

        XCTAssertEqual(store.projects.count, 2)
        XCTAssertEqual(store.projects[0].name, "Alpha")
        XCTAssertEqual(store.projects[1].name, "Beta")
    }

    // MARK: - removeProject

    func testRemoveProjectSucceeds() throws {
        let dir = try makeFakeProjectDir()
        let project = try store.addProject(at: dir)

        try store.removeProject(project.id)

        XCTAssertTrue(store.projects.isEmpty)
    }

    func testRemoveProjectNotFoundThrows() {
        let fakeId = UUID()

        XCTAssertThrowsError(try store.removeProject(fakeId)) { error in
            guard case ProjectManagerError.notFound(let id) = error else {
                return XCTFail("Expected ProjectManagerError.notFound, got \(error)")
            }
            XCTAssertEqual(id, fakeId)
        }
    }

    func testRemoveActiveProjectClearsActiveId() throws {
        let dirA = try makeFakeProjectDir(named: "First")
        let dirB = try makeFakeProjectDir(named: "Second")

        let projectA = try store.addProject(at: dirA)
        let projectB = try store.addProject(at: dirB)

        store.activeProjectId = projectA.id
        try store.removeProject(projectA.id)

        // After removing the active project, activeProjectId should fall back
        // to the first remaining project.
        XCTAssertEqual(store.activeProjectId, projectB.id)
    }

    // MARK: - Duplicate detection

    func testDuplicateProjectThrows() throws {
        let dir = try makeFakeProjectDir(named: "UniqueProject")
        try store.addProject(at: dir)

        XCTAssertThrowsError(try store.addProject(at: dir)) { error in
            guard case ProjectManagerError.duplicate = error else {
                return XCTFail("Expected ProjectManagerError.duplicate, got \(error)")
            }
        }
        XCTAssertEqual(store.projects.count, 1, "Duplicate should not increase project count")
    }

    // MARK: - Forbidden / system path rejection

    func testForbiddenRootPathRejected() {
        // "/" is in PathConstants.forbiddenRootPaths
        XCTAssertThrowsError(try store.addProject(at: URL(fileURLWithPath: "/"))) { error in
            guard case ProjectManagerError.invalidPath = error else {
                return XCTFail("Expected ProjectManagerError.invalidPath, got \(error)")
            }
        }
    }

    func testSystemPathRejected() {
        // "/System" is in PathConstants.forbiddenRootPaths
        XCTAssertThrowsError(try store.addProject(at: URL(fileURLWithPath: "/System"))) { error in
            guard case ProjectManagerError.invalidPath = error else {
                return XCTFail("Expected ProjectManagerError.invalidPath, got \(error)")
            }
        }
    }

    func testNonexistentPathRejected() {
        let bogus = URL(fileURLWithPath: "/tmp/nonexistent-\(UUID().uuidString)")

        XCTAssertThrowsError(try store.addProject(at: bogus)) { error in
            guard case ProjectManagerError.invalidPath = error else {
                return XCTFail("Expected ProjectManagerError.invalidPath, got \(error)")
            }
        }
    }

    func testFilePathRejected() throws {
        // Create a regular file (not a directory) and attempt to add it as a project.
        let file = tempDir.appendingPathComponent("not-a-dir.txt")
        try Data("hello".utf8).write(to: file)

        XCTAssertThrowsError(try store.addProject(at: file)) { error in
            guard case ProjectManagerError.invalidPath = error else {
                return XCTFail("Expected ProjectManagerError.invalidPath, got \(error)")
            }
        }
    }

    // MARK: - Project limit enforcement

    func testProjectLimitReached() throws {
        // The store enforces maxProjects = 32.
        for i in 0..<32 {
            let dir = try makeFakeProjectDir(named: "proj-\(i)")
            try store.addProject(at: dir)
        }
        XCTAssertEqual(store.projects.count, 32)

        let oneMore = try makeFakeProjectDir(named: "proj-overflow")
        XCTAssertThrowsError(try store.addProject(at: oneMore)) { error in
            guard case ProjectManagerError.projectLimitReached(let max) = error else {
                return XCTFail("Expected ProjectManagerError.projectLimitReached, got \(error)")
            }
            XCTAssertEqual(max, 32)
        }
    }

    // MARK: - Save / Load round-trip

    func testSaveLoadRoundtrip() throws {
        let dirA = try makeFakeProjectDir(named: "RoundtripA")
        let dirB = try makeFakeProjectDir(named: "RoundtripB")

        let projectA = try store.addProject(at: dirA)
        let projectB = try store.addProject(at: dirB)

        // Create a fresh store pointing at the same storage directory.
        let store2 = ProjectStore(storageDirectory: tempDir)
        try store2.load()

        XCTAssertEqual(store2.projects.count, 2)
        XCTAssertEqual(store2.projects[0].id, projectA.id)
        XCTAssertEqual(store2.projects[1].id, projectB.id)
        XCTAssertEqual(
            store2.projects[0].path.standardizedFileURL,
            dirA.standardizedFileURL
        )
        XCTAssertEqual(
            store2.projects[1].path.standardizedFileURL,
            dirB.standardizedFileURL
        )
    }

    func testLoadFromEmptyDirectoryProducesEmptyList() throws {
        // A brand-new store with no projects.json should load as empty,
        // not throw an error.
        let freshDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("EmptyStoreTest-\(UUID().uuidString)")
        try FileManager.default.createDirectory(
            at: freshDir,
            withIntermediateDirectories: true
        )
        defer { try? FileManager.default.removeItem(at: freshDir) }

        let freshStore = ProjectStore(storageDirectory: freshDir)
        try freshStore.load()

        XCTAssertTrue(freshStore.projects.isEmpty)
    }

    // MARK: - activeProjectId persistence

    func testActiveProjectIdNotPersistedInProjectsJSON() throws {
        // activeProjectId is not part of the [Project] array encoding.
        // It lives in memory only (or would need AppSessionSnapshot for persistence).
        // Verify that a new store loading the same file does NOT restore activeProjectId.
        let dir = try makeFakeProjectDir(named: "ActiveTest")
        let project = try store.addProject(at: dir)

        store.activeProjectId = project.id
        XCTAssertEqual(store.activeProjectId, project.id)

        let store2 = ProjectStore(storageDirectory: tempDir)
        try store2.load()

        // activeProjectId is NOT encoded in projects.json -- it should be nil.
        XCTAssertNil(
            store2.activeProjectId,
            "activeProjectId is transient and should not survive a fresh load"
        )
    }

    // MARK: - Lookup helpers

    func testProjectLookupById() throws {
        let dir = try makeFakeProjectDir(named: "LookupById")
        let added = try store.addProject(at: dir)

        let found = store.project(for: added.id)
        XCTAssertNotNil(found)
        XCTAssertEqual(found?.id, added.id)
    }

    func testProjectLookupByPath() throws {
        let dir = try makeFakeProjectDir(named: "LookupByPath")
        let added = try store.addProject(at: dir)

        let found = store.project(at: dir)
        XCTAssertNotNil(found)
        XCTAssertEqual(found?.id, added.id)
    }

    func testProjectLookupReturnsNilForUnknownId() {
        XCTAssertNil(store.project(for: UUID()))
    }

    // MARK: - updateProject

    func testUpdateProjectMutatesAndSaves() throws {
        let dir = try makeFakeProjectDir(named: "Updatable")
        let project = try store.addProject(at: dir)

        try store.updateProject(project.id) { p in
            p.name = "Renamed"
        }

        XCTAssertEqual(store.projects.first?.name, "Renamed")

        // Verify persistence.
        let store2 = ProjectStore(storageDirectory: tempDir)
        try store2.load()
        XCTAssertEqual(store2.projects.first?.name, "Renamed")
    }

    func testUpdateNonexistentProjectThrows() {
        XCTAssertThrowsError(try store.updateProject(UUID()) { _ in }) { error in
            guard case ProjectManagerError.notFound = error else {
                return XCTFail("Expected ProjectManagerError.notFound, got \(error)")
            }
        }
    }

    // MARK: - recentProjects

    func testRecentProjectsSortedByLastOpened() throws {
        let dirA = try makeFakeProjectDir(named: "OldProject")
        let dirB = try makeFakeProjectDir(named: "NewProject")

        let projectA = try store.addProject(at: dirA)
        _ = try store.addProject(at: dirB)

        // Force projectA to have the latest lastOpened.
        try store.updateProject(projectA.id) { p in
            p.lastOpened = Date.distantFuture
        }

        XCTAssertEqual(store.recentProjects.first?.id, projectA.id)
    }

    // MARK: - moveProjects

    func testMoveProjectsReorders() throws {
        let dirA = try makeFakeProjectDir(named: "MoveA")
        let dirB = try makeFakeProjectDir(named: "MoveB")
        let dirC = try makeFakeProjectDir(named: "MoveC")

        try store.addProject(at: dirA)
        try store.addProject(at: dirB)
        try store.addProject(at: dirC)

        // Move item at index 2 (MoveC) to index 0.
        store.moveProjects(from: IndexSet(integer: 2), to: 0)

        XCTAssertEqual(store.projects[0].name, "MoveC")
        XCTAssertEqual(store.projects[1].name, "MoveA")
        XCTAssertEqual(store.projects[2].name, "MoveB")
    }
}

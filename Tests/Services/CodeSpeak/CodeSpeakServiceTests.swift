// MARK: - CodeSpeakServiceTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

@MainActor
final class CodeSpeakServiceTests: XCTestCase {

    private var tempDir: URL!

    override func setUpWithError() throws {
        try super.setUpWithError()
        tempDir = URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("CSSvcTests-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
    }

    override func tearDownWithError() throws {
        try? FileManager.default.removeItem(at: tempDir)
        try super.tearDownWithError()
    }

    private func makeProject() -> Project {
        Project(name: "demo", path: tempDir)
    }

    func test_checkConfig_detectsCodespeakJson() throws {
        let project = makeProject()
        let configURL = tempDir.appending(path: "codespeak.json")
        try "{}".write(to: configURL, atomically: true, encoding: .utf8)

        let sut = CodeSpeakService()
        sut.checkConfig(for: project)
        XCTAssertTrue(sut.isCodeSpeakProject(project.id))
        XCTAssertEqual(sut.projectHasConfig[project.id], true)
    }

    func test_checkConfig_absentFile_setsFalse() {
        let project = makeProject()
        let sut = CodeSpeakService()
        sut.checkConfig(for: project)
        XCTAssertFalse(sut.isCodeSpeakProject(project.id))
        XCTAssertEqual(sut.projectHasConfig[project.id], false)
    }

    func test_updateStats_storesPerProject() {
        let sut = CodeSpeakService()
        let projectId = UUID()
        let stats = SpecStats(passing: 4, total: 5, buildDate: Date())
        sut.updateStats(stats, for: projectId)
        XCTAssertEqual(sut.projectStats[projectId], stats)
    }

    func test_clearCache_removesAllEntriesForProject() {
        let sut = CodeSpeakService()
        let projectId = UUID()
        sut.updateStats(SpecStats(passing: 1, total: 1, buildDate: Date()), for: projectId)
        sut.checkConfig(for: Project(id: projectId, name: "x", path: tempDir))

        sut.clearCache(for: projectId)
        XCTAssertNil(sut.projectStats[projectId])
        XCTAssertNil(sut.projectHasConfig[projectId])
    }

    func test_isCodeSpeakProject_unknownIdReturnsFalse() {
        let sut = CodeSpeakService()
        XCTAssertFalse(sut.isCodeSpeakProject(UUID()))
    }
}

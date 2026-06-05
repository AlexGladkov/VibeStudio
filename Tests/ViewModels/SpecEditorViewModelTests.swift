// MARK: - SpecEditorViewModelTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

@MainActor
final class SpecEditorViewModelTests: XCTestCase {

    private var tempDir: URL!

    override func setUpWithError() throws {
        try super.setUpWithError()
        tempDir = URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("SEVMTests-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
    }

    override func tearDownWithError() throws {
        try? FileManager.default.removeItem(at: tempDir)
        try super.tearDownWithError()
    }

    private func makeSpec(content: String = "initial") throws -> SpecFile {
        let url = tempDir.appendingPathComponent("test.cs.md")
        try content.write(to: url, atomically: true, encoding: .utf8)
        return SpecFile(url: url)
    }

    func test_init_loadsFileContent() throws {
        let spec = try makeSpec(content: "hello world")
        let sut = SpecEditorViewModel(specFile: spec)
        XCTAssertEqual(sut.content, "hello world")
        XCTAssertFalse(sut.isDirty)
        XCTAssertNil(sut.saveError)
    }

    func test_init_missingFile_yieldsEmptyContent() {
        let spec = SpecFile(url: tempDir.appendingPathComponent("missing.cs.md"))
        let sut = SpecEditorViewModel(specFile: spec)
        XCTAssertEqual(sut.content, "")
    }

    func test_markDirty_setsDirtyAndClearsError() throws {
        let spec = try makeSpec()
        let sut = SpecEditorViewModel(specFile: spec)
        sut.markDirty()
        XCTAssertTrue(sut.isDirty)
        XCTAssertNil(sut.saveError)
    }

    func test_save_whenNotDirty_returnsTrue_andDoesNotWrite() async throws {
        let spec = try makeSpec(content: "original")
        let sut = SpecEditorViewModel(specFile: spec)
        let ok = await sut.save()
        XCTAssertTrue(ok)
        let onDisk = try String(contentsOf: spec.url, encoding: .utf8)
        XCTAssertEqual(onDisk, "original")
    }

    func test_save_whenDirty_writesContentAndClearsDirty() async throws {
        let spec = try makeSpec(content: "v1")
        let sut = SpecEditorViewModel(specFile: spec)
        sut.content = "v2"
        sut.markDirty()
        let ok = await sut.save()
        XCTAssertTrue(ok)
        XCTAssertFalse(sut.isDirty)
        let onDisk = try String(contentsOf: spec.url, encoding: .utf8)
        XCTAssertEqual(onDisk, "v2")
    }

    func test_save_writeFailure_setsSaveError() async {
        let bogusURL = URL(fileURLWithPath: "/this/path/does/not/exist/spec.cs.md")
        let spec = SpecFile(url: bogusURL)
        let sut = SpecEditorViewModel(specFile: spec)
        sut.content = "anything"
        sut.markDirty()
        let ok = await sut.save()
        XCTAssertFalse(ok)
        XCTAssertNotNil(sut.saveError)
        XCTAssertTrue(sut.isDirty, "Dirty flag must persist after failed save")
    }
}

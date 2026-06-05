// MARK: - CodeSpeakModeViewModelTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

@MainActor
final class CodeSpeakModeViewModelTests: XCTestCase {

    private var codeSpeak: CodeSpeakService!
    private var projectManager: MockProjectManager!
    private var tempDir: URL!

    override func setUp() {
        super.setUp()
        codeSpeak = CodeSpeakService()
        projectManager = MockProjectManager()
        tempDir = URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("CSMode-\(UUID().uuidString)")
        try? FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
    }

    override func tearDown() {
        codeSpeak = nil
        projectManager = nil
        try? FileManager.default.removeItem(at: tempDir)
        tempDir = nil
        super.tearDown()
    }

    private func makeSUT() -> CodeSpeakModeViewModel {
        CodeSpeakModeViewModel(codeSpeak: codeSpeak, projectManager: projectManager)
    }

    private func writeSpec(_ name: String, content: String) throws -> SpecFile {
        let url = tempDir.appendingPathComponent("\(name).cs.md")
        try content.write(to: url, atomically: true, encoding: .utf8)
        return SpecFile(url: url)
    }

    // MARK: - selectSpec

    func test_selectSpec_loadsFileContentsAndClearsDirty() throws {
        let spec = try writeSpec("hello", content: "# Hello\nBody.")

        let sut = makeSUT()
        sut.editorContent = "stale"
        sut.isEditorDirty = false

        sut.selectSpec(spec)

        XCTAssertEqual(sut.selectedSpec?.url, spec.url)
        XCTAssertEqual(sut.editorContent, "# Hello\nBody.")
        XCTAssertNil(sut.selectedGenerated)
        XCTAssertFalse(sut.isEditorDirty)
    }

    func test_selectSpec_missingFile_clearsEditor() {
        let bogus = SpecFile(url: tempDir.appendingPathComponent("does-not-exist.cs.md"))

        let sut = makeSUT()
        sut.editorContent = "stale"
        sut.selectSpec(bogus)

        XCTAssertEqual(sut.editorContent, "",
                       "Editor should be empty when the file cannot be read")
    }

    // MARK: - saveCurrentSpec

    func test_saveCurrentSpec_notDirty_isNoop() async throws {
        let spec = try writeSpec("a", content: "original")
        let sut = makeSUT()
        sut.selectSpec(spec)
        // editorContent mutated only via editor binding; without dirty flag, save skips.
        sut.editorContent = "new content"

        await sut.saveCurrentSpec()

        let onDisk = try String(contentsOf: spec.url, encoding: .utf8)
        XCTAssertEqual(onDisk, "original", "Save must skip when isEditorDirty == false")
        XCTAssertNil(sut.savedAt)
    }

    func test_saveCurrentSpec_dirty_writesAndStampsSavedAt() async throws {
        let spec = try writeSpec("b", content: "original")
        let sut = makeSUT()
        sut.selectSpec(spec)
        sut.editorContent = "updated"
        sut.isEditorDirty = true

        await sut.saveCurrentSpec()

        let onDisk = try String(contentsOf: spec.url, encoding: .utf8)
        XCTAssertEqual(onDisk, "updated")
        XCTAssertFalse(sut.isEditorDirty)
        XCTAssertNotNil(sut.savedAt)
    }

    // MARK: - selectGeneratedFile

    func test_selectGeneratedFile_clearsSpecSelection() throws {
        let spec = try writeSpec("c", content: "doc")
        let sut = makeSUT()
        sut.selectSpec(spec)
        XCTAssertNotNil(sut.selectedSpec)

        let genURL = tempDir.appendingPathComponent("Generated.swift")
        try "// generated".write(to: genURL, atomically: true, encoding: .utf8)
        let gen = GeneratedFile(id: UUID(), url: genURL, specName: "")

        sut.selectGeneratedFile(gen)

        XCTAssertNil(sut.selectedSpec)
        XCTAssertEqual(sut.selectedGenerated?.url, genURL)
        XCTAssertEqual(sut.editorContent, "// generated")
    }
}

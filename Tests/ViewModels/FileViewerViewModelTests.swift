// MARK: - FileViewerViewModelTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

@MainActor
final class FileViewerViewModelTests: XCTestCase {

    private func makeViewed(_ name: String = "a.txt") throws -> (ViewedFile, URL) {
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("vs-fv-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let url = dir.appendingPathComponent(name)
        try Data("hello world".utf8).write(to: url)
        return (ViewedFile(entry: FileEntry(path: url)), dir)
    }

    func test_init_singleFile_titleIsFilename() throws {
        let (vf, dir) = try makeViewed("first.txt")
        defer { try? FileManager.default.removeItem(at: dir) }
        let sut = FileViewerViewModel(initialFile: vf)
        XCTAssertEqual(sut.titleText, "first.txt")
        XCTAssertTrue(sut.canAddMoreFiles)
    }

    func test_addFile_growsTitleToComparing() throws {
        let (vf, dir) = try makeViewed("a.txt")
        defer { try? FileManager.default.removeItem(at: dir) }
        let sut = FileViewerViewModel(initialFile: vf)
        sut.addFile(at: dir.appendingPathComponent("a.txt"))
        XCTAssertEqual(sut.titleText, "Comparing 2 files")
    }

    func test_addFile_thirdCapsCanAddMore() throws {
        let (vf, dir) = try makeViewed("a.txt")
        defer { try? FileManager.default.removeItem(at: dir) }
        let sut = FileViewerViewModel(initialFile: vf)
        sut.addFile(at: dir.appendingPathComponent("a.txt"))
        sut.addFile(at: dir.appendingPathComponent("a.txt"))
        XCTAssertFalse(sut.canAddMoreFiles, "limit is 3 files")
    }

    func test_removeFile_dropsById() throws {
        let (vf, dir) = try makeViewed("a.txt")
        defer { try? FileManager.default.removeItem(at: dir) }
        let sut = FileViewerViewModel(initialFile: vf)
        let id = sut.files.first!.id
        sut.removeFile(id: id)
        XCTAssertTrue(sut.files.isEmpty)
    }
}

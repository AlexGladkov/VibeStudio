// MARK: - SpecsViewModelTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

@MainActor
final class SpecsViewModelTests: XCTestCase {

    private func makeProject(specFiles: [String] = []) throws -> URL {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("vs-specs-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        if !specFiles.isEmpty {
            let specDir = root.appendingPathComponent("spec")
            try FileManager.default.createDirectory(at: specDir, withIntermediateDirectories: true)
            for name in specFiles {
                try Data("# spec\n".utf8).write(to: specDir.appendingPathComponent(name))
            }
        }
        return root
    }

    func test_loadSpecs_noSpecDirectory_clearsList() async throws {
        let root = try makeProject()
        defer { try? FileManager.default.removeItem(at: root) }
        let sut = SpecsViewModel()
        await sut.loadSpecs(at: root)
        XCTAssertTrue(sut.specFiles.isEmpty)
    }

    func test_loadSpecs_picksOnlyCsMdFiles() async throws {
        let root = try makeProject(specFiles: ["a.cs.md", "b.cs.md", "ignore.md", "ignore.txt"])
        defer { try? FileManager.default.removeItem(at: root) }
        let sut = SpecsViewModel()
        await sut.loadSpecs(at: root)
        XCTAssertEqual(sut.specFiles.count, 2)
        XCTAssertEqual(sut.specFiles.map(\.name).sorted(), ["a", "b"])
    }

    func test_updateSpecStatus_updatesMatchingFile() async throws {
        let root = try makeProject(specFiles: ["foo.cs.md"])
        defer { try? FileManager.default.removeItem(at: root) }
        let sut = SpecsViewModel()
        await sut.loadSpecs(at: root)
        sut.updateSpecStatus(name: "foo", status: .passing, pass: 3, total: 3)
        XCTAssertEqual(sut.specFiles.first?.status, .passing)
        XCTAssertEqual(sut.specFiles.first?.passCount, 3)
    }

    func test_updateSpecStatus_unknownName_isNoop() async throws {
        let root = try makeProject(specFiles: ["foo.cs.md"])
        defer { try? FileManager.default.removeItem(at: root) }
        let sut = SpecsViewModel()
        await sut.loadSpecs(at: root)
        sut.updateSpecStatus(name: "unknown", status: .failing, pass: 0, total: 1)
        XCTAssertNotEqual(sut.specFiles.first?.status, .failing)
    }
}

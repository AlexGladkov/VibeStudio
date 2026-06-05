// MARK: - TraceabilityPanelViewModelTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

@MainActor
final class TraceabilityPanelViewModelTests: XCTestCase {

    private func makeProject(specs: [String: String]) throws -> URL {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("vs-trace-\(UUID().uuidString)")
        let specDir = root.appendingPathComponent("spec")
        try FileManager.default.createDirectory(at: specDir, withIntermediateDirectories: true)
        for (name, content) in specs {
            try Data(content.utf8).write(to: specDir.appendingPathComponent(name))
        }
        return root
    }

    func test_scan_noSpecDir_clearsState() async throws {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("vs-trace-empty-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: root) }

        let sut = TraceabilityPanelViewModel()
        await sut.scan(at: root)
        XCTAssertTrue(sut.specToFiles.isEmpty)
        XCTAssertTrue(sut.referencedFiles.isEmpty)
    }

    func test_scan_parsesAtFileMarkers_bidirectional() async throws {
        let root = try makeProject(specs: [
            "auth.cs.md": "# auth\n@file: Sources/Auth/Login.swift\n@file: Sources/Auth/Token.swift\n",
            "profile.cs.md": "# profile\n@file: Sources/Auth/Login.swift\n"
        ])
        defer { try? FileManager.default.removeItem(at: root) }

        let sut = TraceabilityPanelViewModel()
        await sut.scan(at: root)

        XCTAssertEqual(sut.specToFiles.count, 2)
        XCTAssertEqual(sut.fileToSpecs["Sources/Auth/Login.swift"]?.sorted(), ["auth", "profile"])
        XCTAssertEqual(sut.fileToSpecs["Sources/Auth/Token.swift"], ["auth"])
        XCTAssertEqual(sut.referencedFiles.count, 2)
    }

    func test_scan_specWithoutMarkers_omittedFromMap() async throws {
        let root = try makeProject(specs: [
            "empty.cs.md": "# nothing here\n"
        ])
        defer { try? FileManager.default.removeItem(at: root) }
        let sut = TraceabilityPanelViewModel()
        await sut.scan(at: root)
        XCTAssertTrue(sut.specToFiles.isEmpty)
    }
}

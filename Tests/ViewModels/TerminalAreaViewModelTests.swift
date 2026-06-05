// MARK: - TerminalAreaViewModelTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

@MainActor
final class TerminalAreaViewModelTests: XCTestCase {

    func test_createTerminal_callsTerminalManagerWithProjectInfo() throws {
        let pm = MockProjectManager()
        let project = try pm.addProject(at: URL(fileURLWithPath: "/tmp/proj"))
        let tm = MockTerminalSessionManager()

        let sut = TerminalAreaViewModel(projectManager: pm, terminalManager: tm)
        sut.createTerminal(for: project.id)

        XCTAssertEqual(tm.createSessionCallCount, 1)
        XCTAssertEqual(tm.sessionsByProject[project.id]?.count, 1)
        XCTAssertFalse(sut.isCreatingTerminal, "Flag must reset after sync completion")
    }

    func test_createTerminal_unknownProjectIdStillCallsManager() {
        // Even if project not found, the VM still invokes createSession with nil dir/shell.
        let pm = MockProjectManager()
        let tm = MockTerminalSessionManager()
        let sut = TerminalAreaViewModel(projectManager: pm, terminalManager: tm)

        sut.createTerminal(for: UUID())
        XCTAssertEqual(tm.createSessionCallCount, 1)
    }
}

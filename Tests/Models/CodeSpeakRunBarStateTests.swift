// MARK: - CodeSpeakRunBarStateTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

@MainActor
final class CodeSpeakRunBarStateTests: XCTestCase {

    func test_defaults() {
        let sut = CodeSpeakRunBarState()
        XCTAssertEqual(sut.command, .build)
        XCTAssertEqual(sut.taskName, "")
        XCTAssertEqual(sut.changeMessage, "")
        XCTAssertFalse(sut.isRunning)
        XCTAssertFalse(sut.stopRequested)
        XCTAssertEqual(sut.currentSpecName, "")
        XCTAssertFalse(sut.isEditorDirty)
    }

    func test_mutation_isLocal() {
        let sut = CodeSpeakRunBarState()
        sut.command = .test
        sut.taskName = "deploy"
        sut.changeMessage = "fix"
        sut.isRunning = true
        sut.stopRequested = true
        sut.currentSpecName = "my-spec"
        sut.isEditorDirty = true

        XCTAssertEqual(sut.command, .test)
        XCTAssertEqual(sut.taskName, "deploy")
        XCTAssertEqual(sut.changeMessage, "fix")
        XCTAssertTrue(sut.isRunning)
        XCTAssertTrue(sut.stopRequested)
        XCTAssertEqual(sut.currentSpecName, "my-spec")
        XCTAssertTrue(sut.isEditorDirty)
    }

    func test_independentInstances_doNotShareState() {
        let a = CodeSpeakRunBarState()
        let b = CodeSpeakRunBarState()
        a.command = .impl
        XCTAssertEqual(b.command, .build)
    }
}

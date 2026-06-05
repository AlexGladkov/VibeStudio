// MARK: - CodeSpeakCommandTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

final class CodeSpeakCommandTests: XCTestCase {

    func test_displayName_perCase() {
        XCTAssertEqual(CodeSpeakCommand.build.displayName, "Build")
        XCTAssertEqual(CodeSpeakCommand.run.displayName, "Run")
        XCTAssertEqual(CodeSpeakCommand.impl.displayName, "Impl")
        XCTAssertEqual(CodeSpeakCommand.test.displayName, "Test")
        XCTAssertEqual(CodeSpeakCommand.task.displayName, "Task")
        XCTAssertEqual(CodeSpeakCommand.change.displayName, "Change")
    }

    func test_requiresInput_onlyTaskAndChange() {
        XCTAssertTrue(CodeSpeakCommand.task.requiresInput)
        XCTAssertTrue(CodeSpeakCommand.change.requiresInput)
        for c in [CodeSpeakCommand.build, .run, .impl, .test] {
            XCTAssertFalse(c.requiresInput, "\(c) should not require input")
        }
    }

    func test_supportsStatsParsing_onlyBuild() {
        XCTAssertTrue(CodeSpeakCommand.build.supportsStatsParsing)
        for c in [CodeSpeakCommand.run, .impl, .test, .task, .change] {
            XCTAssertFalse(c.supportsStatsParsing)
        }
    }

    func test_cliArguments_build_includesNoInteractive() {
        let args = CodeSpeakCommand.build.cliArguments()
        XCTAssertEqual(args, ["build", "--no-interactive"])
    }

    func test_cliArguments_build_withSpec() {
        let args = CodeSpeakCommand.build.cliArguments(specPath: "/path/to/spec.cs.md")
        XCTAssertEqual(args, ["build", "--no-interactive", "/path/to/spec.cs.md"])
    }

    func test_cliArguments_task_appendsTaskName() {
        let args = CodeSpeakCommand.task.cliArguments(taskName: "deploy")
        XCTAssertEqual(args, ["task", "--no-interactive", "--", "deploy"])
    }

    func test_cliArguments_task_capsLongInput() {
        let huge = String(repeating: "a", count: 1000)
        let args = CodeSpeakCommand.task.cliArguments(taskName: huge)
        // Locate the sanitized task — it follows "--".
        guard let idx = args.firstIndex(of: "--"), idx + 1 < args.count else {
            return XCTFail("Expected task name after --")
        }
        XCTAssertLessThanOrEqual(args[idx + 1].count, 256)
    }

    func test_cliArguments_change_capsLongMessage() {
        let huge = String(repeating: "x", count: 5000)
        let args = CodeSpeakCommand.change.cliArguments(changeMessage: huge)
        guard let idx = args.firstIndex(of: "-m"), idx + 1 < args.count else {
            return XCTFail("Expected message after -m")
        }
        XCTAssertLessThanOrEqual(args[idx + 1].count, 2000)
    }

    func test_cliArguments_change_includesSpec() {
        let args = CodeSpeakCommand.change.cliArguments(specPath: "/spec", changeMessage: "fix typo")
        XCTAssertEqual(args, ["change", "--no-interactive", "/spec", "-m", "fix typo"])
    }

    func test_rawValueRoundtrip() {
        for cmd in CodeSpeakCommand.allCases {
            XCTAssertEqual(CodeSpeakCommand(rawValue: cmd.rawValue), cmd)
        }
    }
}

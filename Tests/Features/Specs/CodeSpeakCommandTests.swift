import XCTest
@testable import VibeStudio

/// Unit tests for ``CodeSpeakCommand.cliArguments(specPath:taskName:changeMessage:)``.
/// Pure argument-array construction — no process spawn, no FS. Focus on the
/// security-relevant invariants: `--no-interactive` is always present, task
/// names are placed after `--` (option-injection guard), and free-text inputs
/// are length-capped.
final class CodeSpeakCommandTests: XCTestCase {

    // Mirror of the private caps in CodeSpeakCommand.
    private let maxTaskNameLength = 256
    private let maxChangeMessageLength = 2000

    // MARK: - Invariant: --no-interactive always present

    func testNoInteractivePresentForEveryCase() {
        for command in CodeSpeakCommand.allCases {
            let args = command.cliArguments(taskName: "t", changeMessage: "m")
            XCTAssertTrue(args.contains("--no-interactive"),
                          "\(command.rawValue) must include --no-interactive")
        }
    }

    // MARK: - Default (build/run/impl/test) structure

    func testBuildNoSpec() {
        XCTAssertEqual(CodeSpeakCommand.build.cliArguments(),
                       ["build", "--no-interactive"])
    }

    func testRunWithSpecPath() {
        XCTAssertEqual(CodeSpeakCommand.run.cliArguments(specPath: "s.cs.md"),
                       ["run", "--no-interactive", "s.cs.md"])
    }

    func testImplAndTestUseRawValueAsFirstArg() {
        XCTAssertEqual(CodeSpeakCommand.impl.cliArguments().first, "impl")
        XCTAssertEqual(CodeSpeakCommand.test.cliArguments().first, "test")
    }

    // MARK: - .task — taskName after `--`

    func testTaskPlacesNameAfterDoubleDash() {
        let args = CodeSpeakCommand.task.cliArguments(taskName: "deploy")
        XCTAssertEqual(args, ["task", "--no-interactive", "--", "deploy"])
    }

    func testTaskWithSpecAppendsSpecFlagAfterName() {
        let args = CodeSpeakCommand.task.cliArguments(specPath: "auth.cs.md",
                                                      taskName: "deploy")
        XCTAssertEqual(args,
                       ["task", "--no-interactive", "--", "deploy", "--spec", "auth.cs.md"])
    }

    func testTaskOptionInjectionNeutralisedByDoubleDash() {
        // A hostile task name that looks like a flag stays as a positional
        // argument because everything after `--` is positional.
        let args = CodeSpeakCommand.task.cliArguments(taskName: "--rm -rf /")
        XCTAssertEqual(args, ["task", "--no-interactive", "--", "--rm -rf /"])
        // The double-dash sits immediately before the injected value.
        let dashIdx = args.firstIndex(of: "--")!
        XCTAssertEqual(args[dashIdx + 1], "--rm -rf /")
    }

    func testTaskNameCappedAtMaxLength() {
        let longName = String(repeating: "a", count: maxTaskNameLength + 100)
        let args = CodeSpeakCommand.task.cliArguments(taskName: longName)
        let injected = args.last!
        XCTAssertEqual(injected.count, maxTaskNameLength)
    }

    func testTaskNameAtLimitNotTruncated() {
        let name = String(repeating: "b", count: maxTaskNameLength)
        let args = CodeSpeakCommand.task.cliArguments(taskName: name)
        XCTAssertEqual(args.last!.count, maxTaskNameLength)
    }

    // MARK: - .change — -m message

    func testChangeStructureNoSpec() {
        let args = CodeSpeakCommand.change.cliArguments(changeMessage: "fix bug")
        XCTAssertEqual(args, ["change", "--no-interactive", "-m", "fix bug"])
    }

    func testChangeWithSpecPathBeforeMessageFlag() {
        let args = CodeSpeakCommand.change.cliArguments(specPath: "s.cs.md",
                                                        changeMessage: "fix bug")
        XCTAssertEqual(args,
                       ["change", "--no-interactive", "s.cs.md", "-m", "fix bug"])
    }

    func testChangeMessageCappedAtMaxLength() {
        let longMsg = String(repeating: "x", count: maxChangeMessageLength + 500)
        let args = CodeSpeakCommand.change.cliArguments(changeMessage: longMsg)
        // `-m` is second-to-last, message is last.
        XCTAssertEqual(args.last!.count, maxChangeMessageLength)
        XCTAssertEqual(args[args.count - 2], "-m")
    }
}

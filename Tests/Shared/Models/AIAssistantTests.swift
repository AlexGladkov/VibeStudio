import XCTest
@testable import VibeStudio

/// Unit tests for ``AIAssistant`` domain model. Pure enum property lookups.
/// Emphasis on the security-relevant `launchCommand(skipPermissions:)` gate:
/// the `--dangerously-skip-permissions` flag must ONLY ever be appended for
/// Claude, and only when explicitly requested.
final class AIAssistantTests: XCTestCase {

    // MARK: - skip-permissions flag (security)

    func testClaudeSkipPermissionsAppendsFlag() {
        let cmd = AIAssistant.claude.launchCommand(skipPermissions: true)
        XCTAssertTrue(cmd.contains("--dangerously-skip-permissions"))
        XCTAssertEqual(cmd, "claude --dangerously-skip-permissions\n")
    }

    func testClaudeWithoutSkipPermissionsHasNoFlag() {
        let cmd = AIAssistant.claude.launchCommand(skipPermissions: false)
        XCTAssertFalse(cmd.contains("--dangerously-skip-permissions"))
        XCTAssertEqual(cmd, "claude\n")
    }

    func testNonClaudeIgnoresSkipPermissionsFlag() {
        for assistant in AIAssistant.allCases where assistant != .claude {
            let cmd = assistant.launchCommand(skipPermissions: true)
            XCTAssertFalse(cmd.contains("--dangerously-skip-permissions"),
                           "\(assistant.rawValue) must never receive the skip-permissions flag")
            // Falls back to the plain launchCommand.
            XCTAssertEqual(cmd, assistant.launchCommand)
        }
    }

    // MARK: - launchViaShellInput

    func testLaunchViaShellInputTrueForOpencodeAndCodeSpeak() {
        XCTAssertTrue(AIAssistant.opencode.launchViaShellInput)
        XCTAssertTrue(AIAssistant.codeSpeak.launchViaShellInput)
    }

    func testLaunchViaShellInputFalseForOthers() {
        for assistant in [AIAssistant.claude, .codex, .gemini, .qwenCode] {
            XCTAssertFalse(assistant.launchViaShellInput,
                           "\(assistant.rawValue) should use a dedicated PTY, not shell input")
        }
    }

    // MARK: - apiKeyEnvironmentVariable

    func testApiKeyEnvironmentVariablePerAgent() {
        XCTAssertNil(AIAssistant.claude.apiKeyEnvironmentVariable)
        XCTAssertNil(AIAssistant.opencode.apiKeyEnvironmentVariable)
        XCTAssertEqual(AIAssistant.codex.apiKeyEnvironmentVariable, "OPENAI_API_KEY")
        XCTAssertEqual(AIAssistant.gemini.apiKeyEnvironmentVariable, "GEMINI_API_KEY")
        XCTAssertEqual(AIAssistant.qwenCode.apiKeyEnvironmentVariable, "DASHSCOPE_API_KEY")
        XCTAssertEqual(AIAssistant.codeSpeak.apiKeyEnvironmentVariable, "ANTHROPIC_API_KEY")
    }

    // MARK: - exitSequence

    func testClaudeExitSequenceCtrlCThenExit() {
        guard case let .ctrlCThenCommand(command) = AIAssistant.claude.exitSequence else {
            return XCTFail("Claude should exit via Ctrl+C then a follow-up command")
        }
        XCTAssertEqual(command, "/exit")
    }

    func testNonClaudeExitSequenceIsCtrlCOnly() {
        for assistant in AIAssistant.allCases where assistant != .claude {
            guard case .ctrlC = assistant.exitSequence else {
                return XCTFail("\(assistant.rawValue) should exit via Ctrl+C only")
            }
        }
    }

    // MARK: - executableName sanity

    func testExecutableNames() {
        XCTAssertEqual(AIAssistant.claude.executableName, "claude")
        XCTAssertEqual(AIAssistant.qwenCode.executableName, "qwen")
        XCTAssertEqual(AIAssistant.codeSpeak.executableName, "codespeak")
    }
}

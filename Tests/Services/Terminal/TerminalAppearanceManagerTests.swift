// MARK: - TerminalAppearanceManagerTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

@MainActor
final class TerminalAppearanceManagerTests: XCTestCase {

    private func envDict(_ env: [String]) -> [String: String] {
        var dict: [String: String] = [:]
        for entry in env {
            guard let eq = entry.firstIndex(of: "=") else { continue }
            dict[String(entry[..<eq])] = String(entry[entry.index(after: eq)...])
        }
        return dict
    }

    func test_buildShellEnvironment_setsTerminalCapabilities() {
        let sut = TerminalAppearanceManager()
        let dict = envDict(sut.buildShellEnvironment())
        XCTAssertEqual(dict["TERM"], "xterm-256color")
        XCTAssertEqual(dict["COLORTERM"], "truecolor")
        XCTAssertNotNil(dict["LANG"])
        XCTAssertEqual(dict["GIT_TERMINAL_PROMPT"], "1")
    }

    func test_buildShellEnvironment_stripsClaudeKeys() {
        // Cannot mutate ProcessInfo, but we can assert these keys never appear in output.
        let sut = TerminalAppearanceManager()
        let dict = envDict(sut.buildShellEnvironment())
        XCTAssertNil(dict["ANTHROPIC_API_KEY"])
        XCTAssertNil(dict["CLAUDECODE"])
        for key in dict.keys {
            XCTAssertFalse(key.hasPrefix("CLAUDE_"), "Stripped CLAUDE_* leaked: \(key)")
        }
    }

    func test_isValidShell_acceptsZsh() {
        // /bin/zsh is the fallback even if /etc/shells is unreadable.
        XCTAssertTrue(TerminalAppearanceManager.isValidShell("/bin/zsh"))
    }

    func test_isValidShell_rejectsArbitraryPath() {
        XCTAssertFalse(TerminalAppearanceManager.isValidShell("/tmp/not-a-shell"))
        XCTAssertFalse(TerminalAppearanceManager.isValidShell("/usr/bin/python3"))
    }
}

// MARK: - AgentEnvironmentBuilderTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

final class AgentEnvironmentBuilderTests: XCTestCase {

    private func envDict(_ env: [String]) -> [String: String] {
        var dict: [String: String] = [:]
        for entry in env {
            guard let eq = entry.firstIndex(of: "=") else { continue }
            let key = String(entry[..<eq])
            let value = String(entry[entry.index(after: eq)...])
            dict[key] = value
        }
        return dict
    }

    func test_alwaysSetsTerminalCapabilities() {
        let dict = envDict(AgentEnvironmentBuilder.build(for: .codex, apiKeyValue: nil))
        XCTAssertEqual(dict["TERM"], "xterm-256color")
        XCTAssertEqual(dict["COLORTERM"], "truecolor")
        XCTAssertNotNil(dict["LANG"])
    }

    func test_injectsAPIKeyForCodex() {
        let dict = envDict(AgentEnvironmentBuilder.build(for: .codex, apiKeyValue: "sk-secret-123"))
        XCTAssertEqual(dict["OPENAI_API_KEY"], "sk-secret-123")
    }

    func test_injectsAPIKeyForGemini() {
        let dict = envDict(AgentEnvironmentBuilder.build(for: .gemini, apiKeyValue: "gem-key"))
        XCTAssertEqual(dict["GEMINI_API_KEY"], "gem-key")
    }

    func test_injectsAPIKeyForCodeSpeak() {
        let dict = envDict(AgentEnvironmentBuilder.build(for: .codeSpeak, apiKeyValue: "ant-key"))
        XCTAssertEqual(dict["ANTHROPIC_API_KEY"], "ant-key")
    }

    func test_claudeDoesNotInjectAPIKey() {
        // Claude has nil apiKeyEnvironmentVariable (OAuth) — even if passed a value, do not set.
        let dict = envDict(AgentEnvironmentBuilder.build(for: .claude, apiKeyValue: "ignored"))
        // No claude-specific key env var; should not pollute env with arbitrary entries.
        XCTAssertNil(dict["ANTHROPIC_API_KEY"], "Claude (OAuth) must not receive ANTHROPIC_API_KEY from this path")
    }

    func test_emptyAPIKey_isNotInjected() {
        let dict = envDict(AgentEnvironmentBuilder.build(for: .codex, apiKeyValue: ""))
        XCTAssertNil(dict["OPENAI_API_KEY"], "Empty key strings should be skipped")
    }

    func test_pathContainsTrustedDirectories() {
        let dict = envDict(AgentEnvironmentBuilder.build(for: .codex, apiKeyValue: nil))
        let path = dict["PATH"] ?? ""
        let parts = path.split(separator: ":").map(String.init)
        for trusted in SecurityConstants.trustedBinDirectories {
            XCTAssertTrue(parts.contains(trusted), "PATH must contain trusted dir \(trusted)")
        }
    }

    func test_doesNotForwardSecrets() {
        // Verify result does NOT contain known sensitive variable names
        // (they may be in parent process — we only forward an allowlist).
        let env = AgentEnvironmentBuilder.build(for: .codex, apiKeyValue: nil)
        let keys = env.compactMap { $0.split(separator: "=").first.map(String.init) }
        for forbidden in ["AWS_ACCESS_KEY_ID", "DATABASE_URL", "GITHUB_TOKEN"] {
            XCTAssertFalse(keys.contains(forbidden), "Must not forward \(forbidden)")
        }
    }
}

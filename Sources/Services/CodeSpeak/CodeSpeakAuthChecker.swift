// MARK: - CodeSpeakAuthChecker
// Resolves whether CodeSpeak has a usable authentication source.
// macOS 14+, Swift 5.10

import Foundation
import OSLog

/// Probes whether CodeSpeak can authenticate, plus the installation status of the CLI.
///
/// CodeSpeak can authenticate via three independent sources, in priority order:
/// 1. **OAuth token** — `~/.codespeak/token.json` populated by `codespeak login`.
/// 2. **Keychain** — `ANTHROPIC_API_KEY` stored via ``KeychainHelper``.
/// 3. **Process environment** — `ANTHROPIC_API_KEY` exported in the parent shell.
///
/// The first match wins; ``hasAuth()`` returns `true` if any source is present.
/// All file system / JSON parsing errors are logged via ``Logger/settings`` and
/// treated as "not authenticated".
@MainActor
public final class CodeSpeakAuthChecker {

    /// Shared singleton — auth probing is stateless and safe to share across panes.
    public static let shared = CodeSpeakAuthChecker()

    /// Public initialiser for tests / DI; production code should prefer ``shared``.
    public init() {}

    // MARK: - Public API

    /// Returns `true` if CodeSpeak has any usable authentication source.
    ///
    /// Checked in priority order: OAuth token → Keychain → process env.
    /// Stateless — safe to call from any actor-bound context.
    public func hasAuth() -> Bool {
        if hasCodeSpeakToken() { return true }
        if let key = KeychainHelper.load(account: "ANTHROPIC_API_KEY"), !key.isEmpty { return true }
        if let key = ProcessInfo.processInfo.environment["ANTHROPIC_API_KEY"], !key.isEmpty { return true }
        return false
    }

    /// Returns `true` if the `codespeak` binary is resolvable via ``CLIAgentPathResolver``.
    public func isCodeSpeakInstalled() -> Bool {
        CLIAgentPathResolver.resolve("codespeak") != nil
    }

    // MARK: - Private Probes

    /// Returns `true` if `~/.codespeak/token.json` contains at least one access token.
    private func hasCodeSpeakToken() -> Bool {
        let url = URL(fileURLWithPath: NSHomeDirectory())
            .appending(path: ".codespeak/token.json")

        let data: Data
        do {
            data = try Data(contentsOf: url)
        } catch {
            // Missing token file is the normal "logged out" state — debug-level only.
            Logger.settings.debug(
                "CodeSpeakAuth: token.json not readable: \(error.localizedDescription, privacy: .public)"
            )
            return false
        }

        let json: [String: Any]?
        do {
            json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        } catch {
            Logger.settings.error(
                "CodeSpeakAuth: token.json malformed: \(error.localizedDescription, privacy: .public)"
            )
            return false
        }

        guard let tokens = json?["tokens"] as? [[String: Any]] else { return false }
        return tokens.contains { ($0["access_token"] as? String)?.isEmpty == false }
    }
}

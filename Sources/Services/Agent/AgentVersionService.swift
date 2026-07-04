// MARK: - AgentVersionService
// Reads installed AI CLI agent versions and runs in-place update commands.
// macOS 14+, Swift 5.10

import Foundation
import Observation
import OSLog

/// Concrete implementation of ``AgentVersionChecking``.
///
/// Reads the installed version by running `<binary> --version` in a background
/// task, parses the first semver match (regex `\d+\.\d+(\.\d+)?`), and caches
/// the result.  Update commands are executed via `/bin/zsh -lc` so that the
/// full PATH from `.zprofile` (Homebrew, npm global, uv, go) is available —
/// mirroring the `launchViaShellInput` agents that depend on the login shell.
@Observable
@MainActor
final class AgentVersionService: AgentVersionChecking {

    // MARK: - Observable State

    private(set) var states: [AIAssistant: AgentVersionState] = [:]
    private(set) var updating: Set<AIAssistant> = []
    private(set) var messages: [AIAssistant: String] = [:]

    // MARK: - Private

    /// Regex for extracting the first semver string from `--version` output.
    ///
    /// Compiled once as a `static let` — `NSRegularExpression` is thread-safe
    /// after initialisation, so it is safe to share across `Task.detached` calls.
    private static let semverRegex: NSRegularExpression = {
        // Pattern is a compile-time constant — force try is safe here.
        // swiftlint:disable:next force_try
        try! NSRegularExpression(pattern: #"\d+\.\d+(\.\d+)?"#)
    }()

    // MARK: - AgentVersionChecking

    func versionState(for agent: AIAssistant) -> AgentVersionState {
        states[agent] ?? .unknown
    }

    func isUpdating(_ agent: AIAssistant) -> Bool {
        updating.contains(agent)
    }

    func lastMessage(for agent: AIAssistant) -> String? {
        messages[agent]
    }

    func refresh(_ agent: AIAssistant) {
        // Mark as checking immediately so the UI reflects the in-progress state
        // before the background task has a chance to run.
        states[agent] = .checking

        let executableName = agent.executableName

        Task.detached(priority: .utility) { [weak self] in
            guard let path = CLIAgentPathResolver.resolve(executableName) else {
                await MainActor.run { [weak self] in
                    self?.states[agent] = .notInstalled
                }
                return
            }

            let (output, _) = AgentVersionService.runProcess(
                executablePath: path,
                arguments: ["--version"]
            )
            let version = AgentVersionService.parseVersion(from: output)

            await MainActor.run { [weak self] in
                guard let self else { return }
                self.states[agent] = .installed(version)
                Logger.services.debug(
                    "AgentVersionService: \(agent.rawValue, privacy: .public) → \(version, privacy: .public)"
                )
            }
        }
    }

    func update(_ agent: AIAssistant) {
        guard let cmd = agent.updateCommand else { return }
        guard !updating.contains(agent) else { return }

        updating.insert(agent)
        messages.removeValue(forKey: agent)

        Task.detached(priority: .userInitiated) { [weak self] in
            // SECURITY: `cmd` is a compile-time static constant defined in
            // AIAssistant.updateCommand — no user-supplied input is interpolated
            // into the shell argument, so shell injection is not possible.
            let (output, exitCode) = AgentVersionService.runProcess(
                executablePath: "/bin/zsh",
                arguments: ["-lc", cmd]
            )

            await MainActor.run { [weak self] in
                guard let self else { return }
                self.updating.remove(agent)

                if exitCode == 0 {
                    self.messages[agent] = String(localized: "Updated")
                    Logger.services.debug(
                        "AgentVersionService: updated \(agent.rawValue, privacy: .public)"
                    )
                } else {
                    // Include the last 3 non-empty lines of output in the error message.
                    let tail = output
                        .components(separatedBy: .newlines)
                        .map { $0.trimmingCharacters(in: .whitespaces) }
                        .filter { !$0.isEmpty }
                        .suffix(3)
                        .joined(separator: " ")
                    let detail = tail.isEmpty ? "unknown error" : tail
                    self.messages[agent] = String(localized: "Update failed") + ": " + detail
                    Logger.services.error(
                        "AgentVersionService: update failed for \(agent.rawValue, privacy: .public): \(detail, privacy: .public)"
                    )
                }
            }

            // Re-check the installed version regardless of success or failure.
            await self?.refresh(agent)
        }
    }

    // MARK: - Private Helpers

    /// Run a subprocess and return combined stdout+stderr output plus exit code.
    ///
    /// `nonisolated static` so it can be called from `Task.detached` without
    /// hopping back to `@MainActor`. `Process` itself is not actor-isolated.
    ///
    /// Data is read from the pipe **before** `waitUntilExit` to prevent the
    /// internal pipe buffer from filling and deadlocking on large outputs.
    nonisolated private static func runProcess(
        executablePath: String,
        arguments: [String]
    ) -> (output: String, exitCode: Int32) {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: executablePath)
        process.arguments = arguments
        // Merge stderr into stdout so we capture any error messages with one pipe.
        let pipe = Pipe()
        process.standardOutput = pipe
        process.standardError = pipe

        do {
            try process.run()
            // Drain the pipe before waitUntilExit to avoid deadlock on large output.
            let data = pipe.fileHandleForReading.readDataToEndOfFile()
            process.waitUntilExit()
            let output = String(data: data, encoding: .utf8) ?? ""
            return (output, process.terminationStatus)
        } catch {
            Logger.services.error(
                "AgentVersionService: process launch failed: \(error.localizedDescription, privacy: .public)"
            )
            return ("", 1)
        }
    }

    /// Extract the first semver string from `--version` output.
    ///
    /// Falls back to the first non-empty trimmed line if no semver pattern
    /// (`\d+\.\d+(\.\d+)?`) is found — handles agents that emit bare
    /// version strings without patch component (e.g. "1.2").
    nonisolated private static func parseVersion(from output: String) -> String {
        let range = NSRange(output.startIndex..., in: output)
        if let match = semverRegex.firstMatch(in: output, range: range),
           let swiftRange = Range(match.range, in: output) {
            return String(output[swiftRange])
        }
        // Fallback: first non-empty trimmed line of output.
        return output
            .components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .first { !$0.isEmpty } ?? "unknown"
    }
}

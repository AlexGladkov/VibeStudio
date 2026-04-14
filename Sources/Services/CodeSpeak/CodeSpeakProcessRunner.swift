// MARK: - CodeSpeakProcessRunner
// Runs codespeak CLI commands and streams stdout lines.
// macOS 14+, Swift 5.10

import Foundation
import OSLog

// MARK: - CodeSpeakOutput

/// Events streamed from a running codespeak process.
enum CodeSpeakOutput: Sendable {
    /// A line of stdout/stderr output.
    case line(String)
    /// The process exited with the given code.
    case exitCode(Int32)
    /// A launch or runtime error (e.g. binary not found, API key missing).
    case error(String)
}

// MARK: - CodeSpeakProcessRunner

/// Background actor that spawns a `codespeak` subprocess and streams its output.
///
/// Uses `CLIAgentPathResolver` to locate the binary from trusted directories.
/// Reads `ANTHROPIC_API_KEY` from Keychain (same path as `AICommitService`).
actor CodeSpeakProcessRunner {

    // MARK: - Process State

    /// The currently running process (nil when idle).
    private var currentProcess: Process?

    /// Monotonic generation counter to guard against stale terminationHandler callbacks.
    private var generation: UInt64 = 0

    // MARK: - Stop

    /// Terminate the currently running process, if any.
    func stop() {
        guard let process = currentProcess, process.isRunning else { return }
        process.terminate()
    }

    /// Clear the process reference if it matches the expected generation.
    ///
    /// Called from `terminationHandler` via `Task { await self?.clearProcess(...) }`
    /// to safely access actor-isolated state.
    private func clearProcess(generation gen: UInt64) {
        guard gen == generation else { return }
        currentProcess = nil
    }

    // MARK: - Run

    /// Spawn `codespeak <args>` in `directory` and stream output events.
    ///
    /// - Parameters:
    ///   - args: Arguments to pass after `codespeak` (e.g. `["build"]`).
    ///   - directory: Working directory for the subprocess.
    ///   - env: Additional environment variables merged with `ANTHROPIC_API_KEY`.
    /// - Returns: An `AsyncStream` of `CodeSpeakOutput` events terminated by `.exitCode`.
    func run(
        _ args: [String],
        at directory: URL,
        env: [String: String] = [:]
    ) -> AsyncStream<CodeSpeakOutput> {
        // Terminate any previously running process before starting a new one.
        currentProcess?.terminate()
        generation &+= 1
        let myGen = generation

        return AsyncStream { continuation in
            Task {
                // 1. Resolve binary
                guard let binaryPath = CLIAgentPathResolver.resolve("codespeak") else {
                    continuation.yield(.error("codespeak not found. Install via: uv tool install codespeak-cli"))
                    continuation.yield(.exitCode(127))
                    continuation.finish()
                    return
                }

                // 2. Resolve API key (Keychain -> process env -> login shell -> .env.local)
                guard let apiKey = await self.resolveAPIKey(at: directory) else {
                    continuation.yield(.error("ANTHROPIC_API_KEY not set. Add it in Settings -> Claude."))
                    continuation.yield(.exitCode(1))
                    continuation.finish()
                    return
                }

                // 3. Build allowlist-based environment
                let allowedVars: Set<String> = [
                    "HOME", "USER", "LOGNAME",
                    "LANG", "LC_ALL", "LC_CTYPE",
                    "TERM", "COLORTERM",
                    "PATH", "SSH_AUTH_SOCK",
                    "SHELL", "TMPDIR",
                    "XDG_CONFIG_HOME", "XDG_DATA_HOME",
                ]
                let parentEnv = ProcessInfo.processInfo.environment
                var processEnv: [String: String] = [:]
                for key in allowedVars {
                    if let value = parentEnv[key] {
                        processEnv[key] = value
                    }
                }

                // Ensure terminal capabilities and locale are always set.
                processEnv["TERM"] = processEnv["TERM"] ?? "xterm-256color"
                processEnv["LANG"] = processEnv["LANG"] ?? "en_US.UTF-8"

                // Prepend trusted bin directories to PATH so codespeak can find
                // its own binary, git, node, and other tools.
                let trustedBins = SecurityConstants.trustedBinDirectories
                let currentPath = processEnv["PATH"] ?? "/usr/bin:/bin:/usr/sbin:/sbin"
                let existingParts = currentPath.split(separator: ":").map(String.init)
                let missingBins = trustedBins.filter { !existingParts.contains($0) }
                if !missingBins.isEmpty {
                    processEnv["PATH"] = (missingBins + existingParts).joined(separator: ":")
                }

                // Inject API key after building the safe env.
                processEnv["ANTHROPIC_API_KEY"] = apiKey
                for (k, v) in env { processEnv[k] = v }

                // 4. Configure and launch process
                let process = Process()
                process.executableURL = URL(fileURLWithPath: binaryPath)
                process.arguments = args
                process.currentDirectoryURL = directory
                process.environment = processEnv

                let pipe = Pipe()
                let errorPipe = Pipe()
                process.standardOutput = pipe
                process.standardError = errorPipe

                // 5. Stream stdout
                let outHandle = pipe.fileHandleForReading
                outHandle.readabilityHandler = { handle in
                    let data = handle.availableData
                    guard !data.isEmpty else { return }
                    if let text = String(data: data, encoding: .utf8) {
                        for line in text.components(separatedBy: "\n") {
                            let trimmed = line.trimmingCharacters(in: .whitespaces)
                            if !trimmed.isEmpty {
                                continuation.yield(.line(trimmed))
                            }
                        }
                    }
                }

                // 6. Stream stderr
                let errHandle = errorPipe.fileHandleForReading
                errHandle.readabilityHandler = { handle in
                    let data = handle.availableData
                    guard !data.isEmpty else { return }
                    if let text = String(data: data, encoding: .utf8) {
                        for line in text.components(separatedBy: "\n") {
                            let trimmed = line.trimmingCharacters(in: .whitespaces)
                            if !trimmed.isEmpty {
                                continuation.yield(.line(trimmed))
                            }
                        }
                    }
                }

                // 7. Launch and handle termination asynchronously
                do {
                    // terminationHandler fires on a Foundation background thread.
                    // Capture only local `let` values; use Task for actor-isolated access.
                    process.terminationHandler = { [weak self] proc in
                        // Drain remaining data from pipes
                        outHandle.readabilityHandler = nil
                        errHandle.readabilityHandler = nil

                        let remainingOut = outHandle.readDataToEndOfFile()
                        if !remainingOut.isEmpty, let text = String(data: remainingOut, encoding: .utf8) {
                            for line in text.components(separatedBy: "\n") {
                                let trimmed = line.trimmingCharacters(in: .whitespaces)
                                if !trimmed.isEmpty { continuation.yield(.line(trimmed)) }
                            }
                        }
                        let remainingErr = errHandle.readDataToEndOfFile()
                        if !remainingErr.isEmpty, let text = String(data: remainingErr, encoding: .utf8) {
                            for line in text.components(separatedBy: "\n") {
                                let trimmed = line.trimmingCharacters(in: .whitespaces)
                                if !trimmed.isEmpty { continuation.yield(.line(trimmed)) }
                            }
                        }

                        continuation.yield(.exitCode(proc.terminationStatus))
                        continuation.finish()

                        // Clear actor state via async hop
                        Task { await self?.clearProcess(generation: myGen) }
                    }

                    try process.run()
                    await self.setCurrentProcess(process, generation: myGen)
                } catch {
                    continuation.yield(.error("Failed to launch codespeak: \(error.localizedDescription)"))
                    continuation.yield(.exitCode(1))
                    continuation.finish()
                }
            }
        }
    }

    /// Store the running process reference if the generation still matches.
    private func setCurrentProcess(_ process: Process, generation gen: UInt64) {
        guard gen == generation else { return }
        currentProcess = process
    }

    // MARK: - Private: API Key Resolution

    /// Resolve ANTHROPIC_API_KEY from Keychain, process env, login shell, or `.env.local`.
    ///
    /// Priority order:
    /// 1. VibeStudio Keychain (user set in Settings → Claude)
    /// 2. Process environment (works when VibeStudio launched from Terminal)
    /// 3. Login shell `.zshenv`/`.zprofile` (covers Finder/Dock launch)
    /// 4. `.env.local` in the project directory (CodeSpeak's own key storage)
    private func resolveAPIKey(at directory: URL? = nil) async -> String? {
        // 1. Keychain — explicit user setting in Settings → Claude (highest priority)
        if let key = KeychainHelper.load(account: "ANTHROPIC_API_KEY"), !key.isEmpty {
            return key
        }
        // 2. Current process environment — works when launched from Terminal
        if let key = ProcessInfo.processInfo.environment["ANTHROPIC_API_KEY"], !key.isEmpty {
            return key
        }
        // 3. Login shell environment — covers Finder/Dock launch (launchd strips shell env)
        if let key = resolveFromLoginShell(), !key.isEmpty {
            return key
        }
        // 4. .env.local in project directory — CodeSpeak stores key here when configured via CLI
        if let dir = directory, let key = resolveFromDotEnv(at: dir) {
            return key
        }
        return nil
    }

    /// Read `ANTHROPIC_API_KEY` from `.env.local` in the given directory.
    ///
    /// CodeSpeak CLI writes the API key to `.env.local` at project init time,
    /// so VibeStudio can pick it up without the user re-entering it.
    private func resolveFromDotEnv(at directory: URL) -> String? {
        let envFile = directory.appending(path: ".env.local")
        guard let content = try? String(contentsOf: envFile, encoding: .utf8) else { return nil }
        for line in content.components(separatedBy: .newlines) {
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            guard trimmed.hasPrefix("ANTHROPIC_API_KEY=") else { continue }
            let value = String(trimmed.dropFirst("ANTHROPIC_API_KEY=".count))
                .trimmingCharacters(in: CharacterSet(charactersIn: "\"' \t"))
            return value.isEmpty ? nil : value
        }
        return nil
    }

    /// Spawn a login shell and read `ANTHROPIC_API_KEY` from it.
    ///
    /// Sources `.zshenv` + `.zprofile` (login shell, non-interactive).
    /// If the key lives only in `.zshrc` this won't find it — user should set
    /// it in Settings → Claude or move the export to `.zshenv`.
    private func resolveFromLoginShell() -> String? {
        let task = Process()
        task.executableURL = URL(fileURLWithPath: "/bin/zsh")
        task.arguments = ["-l", "-c", "printf '%s' \"$ANTHROPIC_API_KEY\""]
        let pipe = Pipe()
        task.standardOutput = pipe
        task.standardError = Pipe() // discard shell startup noise
        do {
            try task.run()
        } catch {
            return nil
        }
        task.waitUntilExit()
        let data = pipe.fileHandleForReading.readDataToEndOfFile()
        guard let raw = String(data: data, encoding: .utf8) else { return nil }
        let value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        return value.isEmpty ? nil : value
    }
}

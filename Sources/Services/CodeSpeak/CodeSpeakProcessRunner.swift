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
            continuation.onTermination = { [weak self] _ in
                Task { await self?.stop() }
            }
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

                // 3. Build allowlist-based environment, then configure + launch.
                let processEnv = Self.buildProcessEnvironment(apiKey: apiKey, extra: env)
                let config = LaunchConfig(
                    binaryPath: binaryPath, args: args, directory: directory, env: processEnv
                )
                await self.launchProcess(config, continuation: continuation, generation: myGen)
            }
        }
    }

    /// Immutable inputs needed to configure + spawn the `codespeak` subprocess.
    private struct LaunchConfig {
        let binaryPath: String
        let args: [String]
        let directory: URL
        let env: [String: String]
    }

    /// Configure the `Process`, wire stdout/stderr streaming + the termination
    /// handler, and launch it. Extracted from ``run(_:at:env:)`` to keep that
    /// method under SwiftLint's `function_body_length` budget.
    private func launchProcess(
        _ config: LaunchConfig,
        continuation: AsyncStream<CodeSpeakOutput>.Continuation,
        generation myGen: UInt64
    ) {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: config.binaryPath)
        process.arguments = config.args
        process.currentDirectoryURL = config.directory
        process.environment = config.env

        let pipe = Pipe()
        let errorPipe = Pipe()
        process.standardOutput = pipe
        process.standardError = errorPipe

        // Stream stdout + stderr as data arrives.
        let outHandle = pipe.fileHandleForReading
        outHandle.readabilityHandler = { handle in
            Self.yieldLines(handle.availableData, into: continuation)
        }
        let errHandle = errorPipe.fileHandleForReading
        errHandle.readabilityHandler = { handle in
            Self.yieldLines(handle.availableData, into: continuation)
        }

        do {
            // terminationHandler fires on a Foundation background thread.
            // Capture only local `let` values; use Task for actor-isolated access.
            process.terminationHandler = { [weak self] proc in
                // Drain remaining data from pipes
                outHandle.readabilityHandler = nil
                errHandle.readabilityHandler = nil

                Self.yieldLines(outHandle.readDataToEndOfFile(), into: continuation)
                Self.yieldLines(errHandle.readDataToEndOfFile(), into: continuation)

                continuation.yield(.exitCode(proc.terminationStatus))
                continuation.finish()

                // Clear actor state via async hop
                Task { await self?.clearProcess(generation: myGen) }
            }

            try process.run()
            setCurrentProcess(process, generation: myGen)
        } catch {
            continuation.yield(.error("Failed to launch codespeak: \(error.localizedDescription)"))
            continuation.yield(.exitCode(1))
            continuation.finish()
        }
    }

    /// Store the running process reference if the generation still matches.
    private func setCurrentProcess(_ process: Process, generation gen: UInt64) {
        guard gen == generation else { return }
        currentProcess = process
    }

    // MARK: - Private: Environment & Output Helpers

    /// Build the allowlist-based subprocess environment for `codespeak`.
    ///
    /// Copies only trusted variables from the parent environment, guarantees
    /// terminal/locale defaults, prepends trusted bin directories to `PATH`,
    /// injects `ANTHROPIC_API_KEY`, then merges any caller-supplied `extra`.
    private nonisolated static func buildProcessEnvironment(
        apiKey: String,
        extra: [String: String]
    ) -> [String: String] {
        let allowedVars: Set<String> = [
            "HOME", "USER", "LOGNAME",
            "LANG", "LC_ALL", "LC_CTYPE",
            "TERM", "COLORTERM",
            "PATH", "SSH_AUTH_SOCK",
            "SHELL", "TMPDIR",
            "XDG_CONFIG_HOME", "XDG_DATA_HOME"
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
        for (k, v) in extra { processEnv[k] = v }
        return processEnv
    }

    /// Split raw pipe `data` into trimmed, non-empty lines and yield each as `.line`.
    private nonisolated static func yieldLines(
        _ data: Data,
        into continuation: AsyncStream<CodeSpeakOutput>.Continuation
    ) {
        guard !data.isEmpty, let text = String(data: data, encoding: .utf8) else { return }
        for line in text.components(separatedBy: "\n") {
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            if !trimmed.isEmpty {
                continuation.yield(.line(trimmed))
            }
        }
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
        if let key = await resolveFromLoginShell(), !key.isEmpty {
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
    ///
    /// Uses async `terminationHandler` instead of `waitUntilExit()` to avoid
    /// blocking the actor thread.
    private func resolveFromLoginShell() async -> String? {
        await withCheckedContinuation { continuation in
            let task = Process()
            task.executableURL = URL(fileURLWithPath: "/bin/zsh")
            task.arguments = ["-l", "-c", "printf '%s' \"$ANTHROPIC_API_KEY\""]
            let pipe = Pipe()
            task.standardOutput = pipe
            task.standardError = Pipe() // discard shell startup noise
            task.terminationHandler = { _ in
                let data = pipe.fileHandleForReading.readDataToEndOfFile()
                guard let raw = String(data: data, encoding: .utf8) else {
                    continuation.resume(returning: nil)
                    return
                }
                let value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
                continuation.resume(returning: value.isEmpty ? nil : value)
            }
            do {
                try task.run()
            } catch {
                continuation.resume(returning: nil)
            }
        }
    }
}

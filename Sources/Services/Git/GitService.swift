// MARK: - GitService
// Stateless git operations via subprocess.
// All commands use argument arrays -- never shell interpolation.
// macOS 14+, Swift 5.10
//
// This file holds the *core*: constants, initialisation, and the private
// subprocess machinery (`runGit` + process helpers). Command groups live in
// dedicated `extension GitService` files:
//   * GitService+Status.swift  — status, ahead/behind, staging, status parsing
//   * GitService+Diff.swift     — diff family, log, diff parsing
//   * GitService+Branch.swift   — branch listing/switching, repo checks, validation
//   * GitService+Remote.swift   — init, remotes, push/pull/fetch
//   * GitService+Commit.swift   — commit

import Foundation

/// Buffer for incrementally draining a subprocess's stdout/stderr pipes.
///
/// A reference type so it can be captured by reference across `@Sendable`
/// closures. All mutations happen on a serial `ioQueue` — no data races.
private final class GitIOBuffer: @unchecked Sendable {
    var stdout = Data()
    var stderr = Data()
}

/// Stateless, thread-safe git CLI wrapper.
///
/// Every git command is executed as a subprocess with strict
/// argument-array invocation to prevent command injection.
/// Branch names are validated before use. File paths are
/// always preceded by `--` to prevent option injection.
///
/// Implemented as a `final class` (not `actor`) because all stored properties
/// are immutable (`let`) — the actor executor added `await` overhead on every
/// call without providing any isolation benefit. All methods are safe to call
/// concurrently since they only read constants and spawn independent subprocesses.
final class GitService: GitServicing, @unchecked Sendable {

    // MARK: - Constants

    /// Default timeout for read-only git operations (seconds).
    private let defaultTimeout: TimeInterval = 30

    /// Extended timeout for network operations like push/pull (seconds).
    ///
    /// `internal` (not `private`) because the network command group lives in
    /// `GitService+Remote.swift` and references it across files.
    let networkTimeout: TimeInterval = 120

    // MARK: - Git Binary

    /// Resolved path to the git binary.
    private let gitPath: String

    init() {
        // Prefer Xcode CLT git, fall back to common paths.
        let candidates = [
            "/usr/bin/git",
            "/usr/local/bin/git",
            "/opt/homebrew/bin/git"
        ]
        self.gitPath = candidates.first { FileManager.default.isExecutableFile(atPath: $0) } ?? "/usr/bin/git"
    }

    // MARK: - Subprocess Execution (shared by every command group)

    /// Execute a git command as a subprocess with strict argument-array invocation.
    ///
    /// **Security**: Never uses `/bin/sh -c`. Arguments are passed directly
    /// to the git binary via `Process.arguments`, preventing shell injection.
    ///
    /// Uses `terminationHandler` instead of `waitUntilExit()` so the calling
    /// thread is never blocked — multiple git commands can be in-flight
    /// concurrently.
    ///
    /// `internal` (not `private`): each command-group extension lives in its
    /// own file and calls `runGit`, which requires module-internal visibility.
    ///
    /// - Parameters:
    ///   - args: Git subcommand and arguments (e.g., `["status", "--porcelain=v1"]`).
    ///   - dir: Working directory for the command.
    ///   - timeout: Maximum execution time in seconds.
    ///   - suppressCredentials: When `true`, disables interactive credential prompts.
    /// - Returns: Standard output as a string.
    /// - Throws: ``GitServiceError`` on failure or timeout.
    @discardableResult
    func runGit(
        _ args: [String],
        in dir: URL,
        timeout: TimeInterval? = nil,
        suppressCredentials: Bool = true
    ) async throws -> String {
        let effectiveTimeout = timeout ?? defaultTimeout
        let commandDesc = args.first ?? "git"
        let gitBinary = self.gitPath

        return try await withCheckedThrowingContinuation { continuation in
            let process = Self.makeGitProcess(
                gitBinary: gitBinary,
                dir: dir,
                args: args,
                suppressCredentials: suppressCredentials
            )

            let stdoutPipe = Pipe()
            let stderrPipe = Pipe()
            process.standardOutput = stdoutPipe
            process.standardError = stderrPipe

            let (buf, ioQueue) = Self.attachDrainingHandlers(
                stdout: stdoutPipe,
                stderr: stderrPipe,
                label: commandDesc
            )

            let timeoutItem = DispatchWorkItem { [weak process] in
                process?.terminate()
            }
            DispatchQueue.global().asyncAfter(deadline: .now() + effectiveTimeout, execute: timeoutItem)

            process.terminationHandler = { proc in
                timeoutItem.cancel()
                let (stdout, stderr) = Self.flush(
                    buffer: buf,
                    stdout: stdoutPipe,
                    stderr: stderrPipe,
                    queue: ioQueue
                )
                continuation.resume(with: GitService.gitResult(GitProcessOutcome(
                    terminationReason: proc.terminationReason,
                    exitStatus: proc.terminationStatus,
                    stdout: stdout,
                    stderr: stderr,
                    commandDesc: commandDesc,
                    timeout: effectiveTimeout,
                    dir: dir
                )))
            }

            do {
                try process.run()
            } catch {
                timeoutItem.cancel()
                continuation.resume(throwing: GitServiceError.gitNotFound)
            }
        }
    }

    /// Attach incremental readability handlers that drain both pipes into a
    /// shared buffer on a serial queue.
    ///
    /// Draining prevents deadlock when git output exceeds the OS pipe buffer
    /// (~64 KB): without it, git blocks writing to the pipe, never exits, and
    /// the timeout eventually fires.
    private static func attachDrainingHandlers(
        stdout stdoutPipe: Pipe,
        stderr stderrPipe: Pipe,
        label: String
    ) -> (buffer: GitIOBuffer, queue: DispatchQueue) {
        let buf = GitIOBuffer()
        let ioQueue = DispatchQueue(label: "git.io.\(label)")
        stdoutPipe.fileHandleForReading.readabilityHandler = { handle in
            let chunk = handle.availableData
            guard !chunk.isEmpty else { return }
            ioQueue.async { buf.stdout.append(chunk) }
        }
        stderrPipe.fileHandleForReading.readabilityHandler = { handle in
            let chunk = handle.availableData
            guard !chunk.isEmpty else { return }
            ioQueue.async { buf.stderr.append(chunk) }
        }
        return (buf, ioQueue)
    }

    /// Disable the draining handlers and flush any trailing bytes, returning
    /// the decoded stdout/stderr strings.
    ///
    /// `ioQueue.sync` guarantees all prior `ioQueue.async` appends completed
    /// before the final `readDataToEndOfFile` read.
    private static func flush(
        buffer buf: GitIOBuffer,
        stdout stdoutPipe: Pipe,
        stderr stderrPipe: Pipe,
        queue ioQueue: DispatchQueue
    ) -> (stdout: String, stderr: String) {
        stdoutPipe.fileHandleForReading.readabilityHandler = nil
        stderrPipe.fileHandleForReading.readabilityHandler = nil
        ioQueue.sync {
            buf.stdout.append(stdoutPipe.fileHandleForReading.readDataToEndOfFile())
            buf.stderr.append(stderrPipe.fileHandleForReading.readDataToEndOfFile())
        }
        let stdout = String(data: buf.stdout, encoding: .utf8) ?? ""
        let stderr = String(data: buf.stderr, encoding: .utf8) ?? ""
        return (stdout, stderr)
    }

    /// Configure the git `Process` (`-C dir` invocation + credential-prompt suppression).
    ///
    /// Uses `git -C <dir>` instead of setting `currentDirectoryURL`. Setting
    /// `currentDirectoryURL` causes the forked child to `chdir(dir)` BEFORE
    /// `execve` — while it still carries VibeStudio's process identity. That
    /// `chdir` into `~/Documents` triggers a TCC dialog on every git subprocess
    /// call. With `-C dir`, git itself (an Apple-signed binary) handles the
    /// chdir internally after exec, which does not trigger TCC.
    private static func makeGitProcess(
        gitBinary: String,
        dir: URL,
        args: [String],
        suppressCredentials: Bool
    ) -> Process {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: gitBinary)
        process.arguments = ["-C", dir.path] + args

        var env = ProcessInfo.processInfo.environment
        if suppressCredentials {
            // Non-network commands: prevent any interactive credential prompt.
            env["GIT_TERMINAL_PROMPT"] = "0"
            env["GIT_ASKPASS"] = "/usr/bin/true"
        }
        // Network commands (suppressCredentials=false) run with the full inherited
        // environment so the system credential helper (osxkeychain / SSH agent) works.
        process.environment = env
        return process
    }

    /// Immutable snapshot of a finished git subprocess.
    ///
    /// Folds the seven fields ``gitResult(_:)`` previously took as separate
    /// parameters into a single value (fixes `function_parameter_count`).
    private struct GitProcessOutcome {
        let terminationReason: Process.TerminationReason
        let exitStatus: Int32
        let stdout: String
        let stderr: String
        let commandDesc: String
        let timeout: TimeInterval
        let dir: URL
    }

    /// Map a finished git process to success (stdout) or a ``GitServiceError``.
    private static func gitResult(_ outcome: GitProcessOutcome) -> Result<String, Error> {
        if outcome.terminationReason == .uncaughtSignal {
            return .failure(GitServiceError.timeout(command: outcome.commandDesc, seconds: outcome.timeout))
        }
        guard outcome.exitStatus == 0 else {
            if outcome.stderr.contains("not a git repository") {
                return .failure(GitServiceError.notARepository(path: outcome.dir))
            }
            return .failure(GitServiceError.commandFailed(
                command: outcome.commandDesc,
                exitCode: outcome.exitStatus,
                stderr: outcome.stderr
            ))
        }
        return .success(outcome.stdout)
    }
}

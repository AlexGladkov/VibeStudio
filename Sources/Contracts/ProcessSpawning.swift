// MARK: - ProcessSpawning
// Abstraction over Foundation.Process for testability.
// macOS 14+, Swift 5.10

import Foundation

// MARK: - ProcessResult

/// Result of a one-shot subprocess invocation.
///
/// - `exitCode`: termination status (0 = success).
/// - `stdout` / `stderr`: captured output as UTF-8 strings.
struct ProcessResult: Sendable, Equatable {
    let exitCode: Int32
    let stdout: String
    let stderr: String
}

// MARK: - ProcessSpawnError

/// Errors thrown by ``ProcessSpawning`` implementations before the subprocess
/// produces a normal termination status (failure to launch, timeout, etc.).
enum ProcessSpawnError: Error, Equatable {
    /// Executable could not be launched (binary missing / not executable).
    case launchFailed(reason: String)
    /// Process exceeded the deadline; it was terminated.
    case timedOut(seconds: TimeInterval)
}

// MARK: - ProcessSpawning

/// Abstraction over `Foundation.Process` so call sites can be unit-tested
/// without forking real subprocesses.
///
/// Two execution modes:
///
/// - ``spawn(executable:args:env:cwd:timeout:)`` — collect stdout/stderr,
///   return when the process exits (or throw on timeout / launch failure).
/// - ``stream(executable:args:env:cwd:)`` — yield merged stdout/stderr lines
///   as they arrive, terminating with the exit code via the stream's last
///   line being `nil` (callers wrap and tag exit code themselves).
///
/// Implementations must be `Sendable`. Returned streams may finish only after
/// the process exits (no buffering after termination).
protocol ProcessSpawning: Sendable {

    /// Run `executable` with `args` until it exits and return the result.
    ///
    /// - Parameters:
    ///   - executable: Absolute path to the binary.
    ///   - args: Command arguments (do NOT include the executable name).
    ///   - env: Environment for the child process. Pass `nil` to inherit.
    ///   - cwd: Working directory; if `nil`, inherits the parent's.
    ///   - timeout: Maximum wall-clock time before SIGTERM is sent. `nil` = no timeout.
    /// - Returns: A ``ProcessResult`` with the captured exit code and output.
    /// - Throws: ``ProcessSpawnError`` if the process could not be launched or
    ///   exceeded `timeout` before exiting.
    func spawn(
        executable: String,
        args: [String],
        env: [String: String]?,
        cwd: URL?,
        timeout: TimeInterval?
    ) async throws -> ProcessResult

    /// Stream the merged stdout+stderr lines of a long-running subprocess.
    ///
    /// The stream finishes when the process exits. Errors thrown into the
    /// stream signal launch failure; exit code is delivered out-of-band via
    /// the implementation-specific caller wrapper (e.g. `CodeSpeakOutput.exitCode`).
    ///
    /// Cancelling the underlying `Task` terminates the subprocess.
    func stream(
        executable: String,
        args: [String],
        env: [String: String]?,
        cwd: URL?
    ) -> AsyncThrowingStream<String, Error>
}

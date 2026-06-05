// MARK: - FoundationProcessSpawner
// Production implementation of ProcessSpawning over Foundation.Process.
// macOS 14+, Swift 5.10

import Foundation

/// `ProcessSpawning` implementation backed by `Foundation.Process`.
///
/// - One-shot ``spawn(executable:args:env:cwd:timeout:)``: drains stdout and
///   stderr through `readabilityHandler` callbacks (no pipe-buffer deadlock),
///   uses `terminationHandler` so the caller's async task is not blocked,
///   and respects a `DispatchWorkItem`-based timeout that sends SIGTERM.
/// - Streaming ``stream(executable:args:env:cwd:)``: yields merged stdout+stderr
///   lines as they arrive and finishes the stream when the process exits.
///   The stream's `onTermination` terminates the subprocess (Task cancellation).
///
/// Thread-safety: `final class` with no shared state. Every invocation creates
/// an independent `Process` and `Pipe` pair.
final class FoundationProcessSpawner: ProcessSpawning {

    init() {}

    // MARK: - One-shot

    func spawn(
        executable: String,
        args: [String],
        env: [String: String]?,
        cwd: URL?,
        timeout: TimeInterval?
    ) async throws -> ProcessResult {
        try await withCheckedThrowingContinuation { continuation in
            let process = Process()
            process.executableURL = URL(fileURLWithPath: executable)
            process.arguments = args
            if let cwd { process.currentDirectoryURL = cwd }
            if let env { process.environment = env }

            let stdoutPipe = Pipe()
            let stderrPipe = Pipe()
            process.standardOutput = stdoutPipe
            process.standardError = stderrPipe

            // IOBuffer is a class so it can be captured by reference across
            // @Sendable closures. All mutations happen on ioQueue (serial).
            final class IOBuffer: @unchecked Sendable {
                var stdout = Data()
                var stderr = Data()
            }
            let buf = IOBuffer()
            let ioQueue = DispatchQueue(label: "process.io.\((executable as NSString).lastPathComponent)")

            // Drain pipes incrementally to prevent deadlock when subprocess
            // output exceeds the OS pipe buffer (~64 KB).
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

            // Timeout: send SIGTERM to the child.
            let timeoutSeconds = timeout
            let timeoutItem: DispatchWorkItem?
            if let timeoutSeconds {
                let item = DispatchWorkItem { [weak process] in
                    process?.terminate()
                }
                timeoutItem = item
                DispatchQueue.global().asyncAfter(deadline: .now() + timeoutSeconds, execute: item)
            } else {
                timeoutItem = nil
            }

            process.terminationHandler = { proc in
                timeoutItem?.cancel()

                stdoutPipe.fileHandleForReading.readabilityHandler = nil
                stderrPipe.fileHandleForReading.readabilityHandler = nil
                ioQueue.sync {
                    buf.stdout.append(stdoutPipe.fileHandleForReading.readDataToEndOfFile())
                    buf.stderr.append(stderrPipe.fileHandleForReading.readDataToEndOfFile())
                }

                let stdout = String(data: buf.stdout, encoding: .utf8) ?? ""
                let stderr = String(data: buf.stderr, encoding: .utf8) ?? ""

                if proc.terminationReason == .uncaughtSignal, let timeoutSeconds {
                    continuation.resume(throwing: ProcessSpawnError.timedOut(seconds: timeoutSeconds))
                    return
                }

                continuation.resume(returning: ProcessResult(
                    exitCode: proc.terminationStatus,
                    stdout: stdout,
                    stderr: stderr
                ))
            }

            do {
                try process.run()
            } catch {
                timeoutItem?.cancel()
                continuation.resume(throwing: ProcessSpawnError.launchFailed(reason: error.localizedDescription))
            }
        }
    }

    // MARK: - Streaming

    func stream(
        executable: String,
        args: [String],
        env: [String: String]?,
        cwd: URL?
    ) -> AsyncThrowingStream<String, Error> {
        AsyncThrowingStream { continuation in
            let process = Process()
            process.executableURL = URL(fileURLWithPath: executable)
            process.arguments = args
            if let cwd { process.currentDirectoryURL = cwd }
            if let env { process.environment = env }

            let stdoutPipe = Pipe()
            let stderrPipe = Pipe()
            process.standardOutput = stdoutPipe
            process.standardError = stderrPipe

            let outHandle = stdoutPipe.fileHandleForReading
            let errHandle = stderrPipe.fileHandleForReading

            let yieldLines: @Sendable (Data) -> Void = { data in
                guard let text = String(data: data, encoding: .utf8) else { return }
                for line in text.components(separatedBy: "\n") {
                    let trimmed = line.trimmingCharacters(in: .whitespaces)
                    if !trimmed.isEmpty {
                        continuation.yield(trimmed)
                    }
                }
            }

            outHandle.readabilityHandler = { handle in
                let data = handle.availableData
                if !data.isEmpty { yieldLines(data) }
            }
            errHandle.readabilityHandler = { handle in
                let data = handle.availableData
                if !data.isEmpty { yieldLines(data) }
            }

            process.terminationHandler = { _ in
                outHandle.readabilityHandler = nil
                errHandle.readabilityHandler = nil
                let remOut = outHandle.readDataToEndOfFile()
                if !remOut.isEmpty { yieldLines(remOut) }
                let remErr = errHandle.readDataToEndOfFile()
                if !remErr.isEmpty { yieldLines(remErr) }
                continuation.finish()
            }

            continuation.onTermination = { @Sendable _ in
                if process.isRunning { process.terminate() }
            }

            do {
                try process.run()
            } catch {
                continuation.finish(throwing: ProcessSpawnError.launchFailed(reason: error.localizedDescription))
            }
        }
    }
}

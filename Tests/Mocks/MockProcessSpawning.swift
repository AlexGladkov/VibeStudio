// MARK: - MockProcessSpawning
// Programmable mock for ProcessSpawning used in GitService unit tests.
// macOS 14+, Swift 5.10

import Foundation
@testable import VibeStudio

/// Recorded invocation of ``MockProcessSpawning/spawn(executable:args:env:cwd:timeout:)``.
struct RecordedSpawn: Sendable, Equatable {
    let executable: String
    let args: [String]
    let env: [String: String]?
    let cwd: URL?
    let timeout: TimeInterval?
}

/// Programmable ``ProcessSpawning`` mock.
///
/// Configure responses via ``setNextResult(_:)`` / ``setNextError(_:)`` or
/// ``setResponder(_:)`` for full control over multi-call sequences.
/// Inspect calls via ``calls`` after the SUT exercises the spawner.
final class MockProcessSpawning: ProcessSpawning, @unchecked Sendable {

    private let lock = NSLock()
    private var _calls: [RecordedSpawn] = []
    private var responder: (@Sendable (RecordedSpawn) -> Result<ProcessResult, Error>)?
    private var queue: [Result<ProcessResult, Error>] = []
    private var defaultResult: Result<ProcessResult, Error> = .success(
        ProcessResult(exitCode: 0, stdout: "", stderr: "")
    )

    init() {}

    // MARK: - Inspection

    var calls: [RecordedSpawn] {
        lock.lock(); defer { lock.unlock() }
        return _calls
    }

    var callCount: Int { calls.count }

    var lastCall: RecordedSpawn? { calls.last }

    /// Args of the most recent call, stripped of the leading `-C <path>` pair
    /// that ``GitService`` always prepends. Empty array if no call was made.
    var lastGitArgs: [String] {
        guard let last = lastCall else { return [] }
        if last.args.count >= 2, last.args[0] == "-C" {
            return Array(last.args.dropFirst(2))
        }
        return last.args
    }

    // MARK: - Programming

    /// Enqueue a single result to return on the next `spawn` call.
    /// Calls beyond the queue length fall back to ``setDefaultResult(_:)``.
    func enqueue(_ result: ProcessResult) {
        lock.lock(); defer { lock.unlock() }
        queue.append(.success(result))
    }

    /// Enqueue a thrown error for the next `spawn` call.
    func enqueueError(_ error: Error) {
        lock.lock(); defer { lock.unlock() }
        queue.append(.failure(error))
    }

    /// Set a single result returned for every call (until overridden).
    func setDefaultResult(_ result: ProcessResult) {
        lock.lock(); defer { lock.unlock() }
        defaultResult = .success(result)
    }

    /// Set a closure responsible for returning a result based on the call.
    func setResponder(_ responder: @escaping @Sendable (RecordedSpawn) -> Result<ProcessResult, Error>) {
        lock.lock(); defer { lock.unlock() }
        self.responder = responder
    }

    // MARK: - ProcessSpawning

    func spawn(
        executable: String,
        args: [String],
        env: [String: String]?,
        cwd: URL?,
        timeout: TimeInterval?
    ) async throws -> ProcessResult {
        let recorded = RecordedSpawn(
            executable: executable,
            args: args,
            env: env,
            cwd: cwd,
            timeout: timeout
        )
        lock.lock()
        _calls.append(recorded)
        let outcome: Result<ProcessResult, Error>
        if let responder {
            outcome = responder(recorded)
        } else if !queue.isEmpty {
            outcome = queue.removeFirst()
        } else {
            outcome = defaultResult
        }
        lock.unlock()
        return try outcome.get()
    }

    func stream(
        executable: String,
        args: [String],
        env: [String: String]?,
        cwd: URL?
    ) -> AsyncThrowingStream<String, Error> {
        AsyncThrowingStream { continuation in
            continuation.finish()
        }
    }
}

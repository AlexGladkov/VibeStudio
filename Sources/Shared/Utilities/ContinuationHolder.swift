// MARK: - ContinuationHolder
// Thread-safe CheckedContinuation wrapper for observation-tracking bridges.
// macOS 14+, Swift 5.10

import Foundation

/// Thread-safe wrapper for a `CheckedContinuation<Void, Never>`.
///
/// Bridges `withObservationTracking`'s synchronous `onChange` callback
/// (which fires from a non-isolated context) with a checked async continuation.
/// The NSLock guarantees that `resume()` is idempotent and safe to call from
/// any thread — including concurrent calls from `onChange` and task cancellation.
final class ContinuationHolder: @unchecked Sendable {
    private let lock = NSLock()
    private var continuation: CheckedContinuation<Void, Never>?

    func set(_ continuation: CheckedContinuation<Void, Never>) {
        lock.lock()
        self.continuation = continuation
        lock.unlock()
    }

    func resume() {
        lock.lock()
        let c = continuation
        continuation = nil
        lock.unlock()
        c?.resume()
    }
}

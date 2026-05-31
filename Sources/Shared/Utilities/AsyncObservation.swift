// MARK: - AsyncObservation
// Bridges `@Observable` property changes into an `AsyncStream`.
// macOS 14+, Swift 5.10

import Foundation
import Observation

/// Utility namespace for bridging Swift's `Observation` framework to
/// `AsyncStream`s.
///
/// `withObservationTracking` only delivers a single `onChange` per call.
/// To react to *every* mutation we have to re-arm the tracker after each
/// callback. This helper hides that boilerplate behind an
/// `AsyncStream<V>` so callers can simply `for await value in stream`.
///
/// The bridge is cancellation-safe:
/// - When the consuming `Task` is cancelled, the stream is `finish()`-ed
///   and any pending continuation is resumed.
/// - The underlying ``ContinuationHolder`` guards against double-resume
///   when `onChange` and `onCancel` race.
///
/// Example:
/// ```swift
/// let task = Task { @MainActor in
///     let stream = AsyncObservation.stream(of: themeService, keyPath: \.selectedAppearance)
///     for await appearance in stream {
///         refreshColors(for: appearance)
///     }
/// }
/// ```
enum AsyncObservation {

    /// Bridges an `@Observable` property to an `AsyncStream`.
    ///
    /// - Parameters:
    ///   - target: The `@Observable` object to observe. Held weakly.
    ///   - keyPath: KeyPath into the tracked property. The keypath read
    ///     is executed inside `withObservationTracking { ... }` so any
    ///     dependency the getter touches is also tracked.
    ///   - emitInitial: When `true` (default) the current value is yielded
    ///     once before the first observation tick. Set to `false` if the
    ///     caller already has the baseline.
    /// - Returns: An `AsyncStream<V>` that yields whenever the observed
    ///   property mutates. The stream finishes when `target` is
    ///   deallocated or when the consuming `Task` is cancelled.
    @MainActor
    static func stream<T: AnyObject, V: Sendable>(
        of target: T,
        keyPath: KeyPath<T, V>,
        emitInitial: Bool = true
    ) -> AsyncStream<V> {
        let weakTarget = WeakBox(target)
        return stream(
            emitInitial: emitInitial,
            read: { [weakTarget] in
                guard let target = weakTarget.value else { return nil }
                return target[keyPath: keyPath]
            }
        )
    }

    /// Closure-based variant for cases where a key path is impractical
    /// (e.g. observing a property through an `any Protocol` existential or
    /// observing a derived value like `Set(projects.map(\.id))`).
    ///
    /// The `read` closure must:
    /// - Touch every `@Observable` property whose changes should re-arm the
    ///   tracker (any read inside the closure counts).
    /// - Return `nil` when the observation target is deallocated so the
    ///   stream can finish.
    ///
    /// - Parameters:
    ///   - emitInitial: When `true` (default) the current value is yielded
    ///     once before the first observation tick.
    ///   - read: Synchronous closure that performs the tracked reads and
    ///     returns the value to yield.
    @MainActor
    static func stream<V: Sendable>(
        emitInitial: Bool = true,
        read: @escaping @MainActor () -> V?
    ) -> AsyncStream<V> {
        AsyncStream<V> { continuation in
            if emitInitial, let initial = read() {
                continuation.yield(initial)
            }

            let task = Task { @MainActor in
                while !Task.isCancelled {
                    let holder = ContinuationHolder()
                    await withTaskCancellationHandler {
                        await withCheckedContinuation { (cont: CheckedContinuation<Void, Never>) in
                            holder.set(cont)
                            withObservationTracking {
                                _ = read()
                            } onChange: {
                                holder.resume()
                            }
                        }
                    } onCancel: {
                        holder.resume()
                    }

                    guard !Task.isCancelled else {
                        continuation.finish()
                        return
                    }
                    guard let value = read() else {
                        continuation.finish()
                        return
                    }
                    continuation.yield(value)
                }
                continuation.finish()
            }

            continuation.onTermination = { _ in
                task.cancel()
            }
        }
    }
}

// MARK: - WeakBox

/// Minimal weak reference wrapper so the observation task does not retain
/// the target. Keeping a strong reference would defeat the @Observable
/// lifecycle invariants (deinit must run even while observers are alive).
private final class WeakBox<T: AnyObject>: @unchecked Sendable {
    weak var value: T?
    init(_ value: T) { self.value = value }
}

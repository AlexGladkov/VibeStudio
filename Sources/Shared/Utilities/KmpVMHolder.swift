// MARK: - KmpVMHolder
// Explicit-lifecycle owner for KMP `BaseViewModel`-derived view models.
// macOS 14+, Swift 5.10
//
// ## Lifecycle model
//
// KMP view-models carry a Kotlin `CoroutineScope` that must be cancelled
// deterministically (and *awaited* — `disposeAndJoin`) when the Swift view
// disappears. Swift `deinit` runs on whichever thread releases the last strong
// reference and cannot `await`, so it is NOT a safe place to do real cleanup.
//
// Use the explicit SwiftUI pattern:
//
// ```swift
// struct MyScreen: View {
//     @State private var holder = KmpVMHolder(SharedFactory.makeMyViewModel(...))
//
//     var body: some View {
//         Content()
//             .task { await holder.activate() }       // optional warm-up
//             .onDisappear {
//                 Task { await holder.dispose() }     // structured teardown
//             }
//     }
// }
// ```
//
// `deinit` retains a *fire-and-forget* fallback that logs a warning so leaks
// surface in the OS log instead of silently piling up cancelled scopes.

import Foundation
import OSLog

/// Anything that owns asynchronous resources (a Kotlin `CoroutineScope`, an
/// open file handle, a subscription) and can be deterministically released.
///
/// KMP `BaseViewModel` should conform to this protocol via a small Swift
/// extension in the framework wrapper module:
///
/// ```swift
/// extension VibeStudioShared.BaseViewModel: Disposable {
///     public func dispose() async { await disposeAndJoin() }
/// }
/// ```
protocol Disposable: AnyObject, Sendable {
    /// Cancel any owned resources and wait for in-flight work to complete.
    /// Must be safe to call multiple times — implementations should be
    /// idempotent.
    func dispose() async
}

/// Owns a KMP view-model and enforces a single, explicit `dispose()` call.
///
/// - Important: Always pair construction with an explicit `dispose()` from the
///   owning SwiftUI view (`.onDisappear { Task { await holder.dispose() } }`).
///   The `deinit` fallback is a *safety net*, not the intended teardown path.
final class KmpVMHolder<VM: Disposable>: @unchecked Sendable {

    /// The wrapped KMP view-model instance. Read-only after init.
    let vm: VM

    /// Tracks whether `dispose()` has run. Locked because `deinit` is
    /// nonisolated and may execute on any thread.
    private let disposedFlag = DisposeFlag()

    /// Create a holder owning `vm`. The view-model is retained until
    /// `dispose()` is called (or the holder is deallocated).
    init(_ vm: VM) {
        self.vm = vm
    }

    /// Optional warm-up hook. Default is a no-op; future revisions may use it
    /// to kick off initial `StateFlow` collection or background prefetches.
    func activate() async { /* reserved */ }

    /// Tear down the wrapped view-model. Idempotent across threads.
    func dispose() async {
        guard disposedFlag.tryMarkDisposed() else { return }
        await vm.dispose()
    }

    deinit {
        // Contract: explicit `dispose()` should already have run by the time
        // the view disappears. If not, fall back to a detached Task and log
        // loudly so the leak is visible in Console.app / OSLog.
        if !disposedFlag.isDisposed {
            Logger(subsystem: "tech.mobiledeveloper.vibestudio", category: "KmpVMHolder")
                .error("KmpVMHolder<\(String(describing: VM.self), privacy: .public)> deallocated without explicit dispose() — falling back to detached Task. Fix the call site to call `await holder.dispose()` from .onDisappear.")
            let vmRef = vm
            Task.detached { await vmRef.dispose() }
        }
    }
}

/// Tiny lock-protected boolean usable from any isolation context (incl. `deinit`).
/// Kept private to this file — not a general-purpose primitive.
private final class DisposeFlag: @unchecked Sendable {
    private let lock = NSLock()
    private var value: Bool = false

    /// Returns `true` exactly once: on the first call that transitions the
    /// flag from `false` → `true`. Subsequent calls return `false`.
    func tryMarkDisposed() -> Bool {
        lock.lock(); defer { lock.unlock() }
        guard !value else { return false }
        value = true
        return true
    }

    var isDisposed: Bool {
        lock.lock(); defer { lock.unlock() }
        return value
    }
}

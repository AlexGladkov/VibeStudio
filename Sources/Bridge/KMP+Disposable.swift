// MARK: - VibeStudioShared bridge: BaseViewModel → Disposable
// Conforms KMP `BaseViewModel` to the Swift `Disposable` protocol so it can
// be owned by `KmpVMHolder<VM>` with the explicit lifecycle pattern.
// macOS 14+, Swift 5.10

import Foundation
import VibeStudioShared

/// Bridge KMP `BaseViewModel` to the Swift `Disposable` contract.
///
/// `disposeAndJoin()` is SKIE-generated as `async throws`. We swallow any
/// cancellation error here because:
/// - Kotlin's `CoroutineScope.cancel()` is documented as idempotent.
/// - The only error this call can surface is a Kotlin `CancellationException`,
///   which is the expected outcome of disposal — propagating it would force
///   every call site to handle a benign condition.
extension VibeStudioShared.BaseViewModel: @retroactive Disposable {
    /// Cancel the underlying Kotlin scope and await completion of in-flight work.
    public func dispose() async {
        do {
            try await disposeAndJoin()
        } catch {
            // Cancellation during teardown is expected — see doc above.
        }
    }
}

/// `BaseViewModel` is a Kotlin/Native `NSObject` subclass. SKIE does not
/// declare it `Sendable`, but the underlying Kotlin implementation guards
/// its mutable state with a `SupervisorJob` scope and is safe to hand off
/// between Swift isolation contexts as a reference.
///
/// We use `@unchecked` because the Swift compiler cannot verify Kotlin's
/// internal synchronization. Audited 2026-06-05.
extension VibeStudioShared.BaseViewModel: @retroactive @unchecked Sendable {}

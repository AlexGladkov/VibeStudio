// MARK: - VibeStudioShared bridge: BaseViewModel → Disposable
// Conforms KMP `BaseViewModel` to the Swift `Disposable` protocol so it can
// be owned by `KmpVMHolder<VM>` with the explicit lifecycle pattern.
// macOS 14+, Swift 5.10

import Foundation
import os
import VibeStudioShared

private let bridgeLog = Logger(subsystem: "studio.vibe", category: "KMPDisposable")

/// Bridge KMP `BaseViewModel` to the Swift `Disposable` contract.
///
/// `disposeAndJoin()` is SKIE-generated as `async throws`. We swallow
/// `CancellationException` (the only expected throw from Kotlin scope teardown)
/// and log any unexpected error at `.error` level so it is surfaced in
/// Console.app and `log stream` without crashing the caller.
extension VibeStudioShared.BaseViewModel: @retroactive Disposable {
    /// Cancel the underlying Kotlin scope and await completion of in-flight work.
    public func dispose() async {
        do {
            try await disposeAndJoin()
        } catch {
            // CancellationException is the documented, expected outcome of disposal.
            // Any other error is unexpected and should be visible to engineers.
            let desc = String(describing: error)
            if desc.contains("CancellationException") {
                // Expected — swallow silently.
            } else {
                bridgeLog.error("BaseViewModel.dispose() unexpected error: \(desc, privacy: .public)")
            }
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

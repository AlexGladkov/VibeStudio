// MARK: - StateFlowProxy
// @Observable bridge from Kotlin `StateFlow<T>` to Swift SwiftUI.
// macOS 14+, Swift 5.10
//
// STATUS (sprint-5): The placeholder `bind(to: Any)` implementation has been
// REMOVED. A no-op bridge is more dangerous than no bridge — call sites would
// silently never receive updates. Use the real SKIE-generated AsyncSequence
// directly (see STATEFLOW_BRIDGE.md in this directory for the planned design)
// or use the explicit `update(_:)` method below for manual/test wiring.

import Foundation
import Observation

/// Generic `@Observable` proxy that mirrors the latest value of a Kotlin
/// `StateFlow<Value>` into a SwiftUI-observable property.
///
/// - Important: Must be created on the main actor (it publishes values that
///   drive SwiftUI updates).
///
/// ### Real binding (planned)
/// Once `VibeStudioShared.framework` is wired into the Xcode target, SKIE
/// exposes Kotlin `StateFlow<T>` as `SkieSwiftStateFlow<T>` conforming to
/// `AsyncSequence`. Consumers should iterate directly:
///
/// ```swift
/// .task {
///     for await next in holder.vm.uiState {
///         proxy.update(next)
///     }
/// }
/// ```
///
/// Keeping the consumption loop at the call site (instead of hidden inside a
/// `bind(_:)` helper) makes cancellation explicit (SwiftUI `.task` cancels on
/// view disappear) and avoids retain cycles. See `STATEFLOW_BRIDGE.md`.
@MainActor
@Observable
final class StateFlowProxy<Value: Sendable> {

    /// Latest value emitted by the upstream StateFlow.
    private(set) var value: Value

    /// Create a proxy seeded with `initial` — used until the first emission
    /// from the upstream StateFlow is received.
    init(initial: Value) {
        self.value = initial
    }

    /// Publish a new value. Call from a `for await` loop over the
    /// SKIE-generated `AsyncSequence`, or directly from tests.
    func update(_ next: Value) {
        self.value = next
    }
}

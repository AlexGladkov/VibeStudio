// MARK: - StateFlowProxy
// @Observable bridge from Kotlin `StateFlow<T>` to Swift SwiftUI.
// macOS 14+, Swift 5.10
//
// Pattern
// -------
// KMP exposes UI state as `StateFlow<UiState>`. SwiftUI observes via
// `@Observable`. This proxy collects values from the StateFlow on a long-lived
// `Task`, publishes them to an `@Observable` property, and cancels the task on
// `dispose()` (called from `KmpVMHolder.deinit`).
//
// Usage (SwiftUI)
// ---------------
// ```swift
// @State private var proxy = StateFlowProxy<MyState>(initial: .empty)
//
// .task {
//     await proxy.bind(to: holder.vm.stateFlow)   // SkieSwiftStateFlow<MyState>
// }
// ```
//
// SKIE notes
// ----------
// When SKIE is present, Kotlin `StateFlow<T>` is exposed to Swift as
// `SkieSwiftStateFlow<T>` which conforms to `AsyncSequence`. This proxy
// consumes that AsyncSequence with a `for await` loop. The Kotlin generic
// `T` becomes a strongly-typed Swift generic — no `Any` casts needed.
//
// Without SKIE the bridge degrades: Kotlin `StateFlow` arrives in Swift as
// `Kotlinx_coroutines_coreStateFlow` (existential, untyped). The `#if SKIE`
// branch below is the canonical implementation; the `#else` branch is a
// best-effort stub that pulls values via a Kotlin-provided `collect`
// extension and explicit casts. Replace `BindHandle` with the real SKIE type
// once the bridging plan settles.

import Foundation
import Observation

/// Generic `@Observable` proxy that mirrors the latest value of a Kotlin
/// `StateFlow<Value>` into a SwiftUI-observable property.
///
/// - Important: Must be created on the main actor (it publishes values that
///   drive SwiftUI updates). The internal collection task hops onto
///   `MainActor` before each publish.
@MainActor
@Observable
final class StateFlowProxy<Value: AnyObject & Sendable> {

    /// Latest value emitted by the upstream StateFlow.
    private(set) var value: Value

    /// Active collection task; cancelled on `dispose()` or re-bind.
    private var collectionTask: Task<Void, Never>?

    /// Create a proxy seeded with `initial` — used until the first emission
    /// from the upstream StateFlow is received.
    init(initial: Value) {
        self.value = initial
    }

    /// Bind to a Kotlin StateFlow. Cancels any previous binding.
    ///
    /// - Parameter stateFlow: Type-erased reference to a Kotlin `StateFlow`.
    ///   When SKIE is integrated, replace `Any` with `SkieSwiftStateFlow<Value>`
    ///   and the body with a `for await` loop.
    func bind(to stateFlow: Any) {
        collectionTask?.cancel()
        collectionTask = Task { [weak self] in
            // TODO(sprint-5): replace with SKIE-generated AsyncSequence:
            //
            //   guard let typed = stateFlow as? SkieSwiftStateFlow<Value> else { return }
            //   for await next in typed {
            //       guard let self else { return }
            //       await MainActor.run { self.value = next }
            //   }
            //
            // Until SKIE bindings are imported into Sources/, this is a no-op
            // placeholder so that consumer call sites can be written today
            // and start working as soon as the shared framework is added.
            _ = self
            _ = stateFlow
        }
    }

    /// Cancel the collection task. Idempotent.
    ///
    /// Call from `KmpVMHolder`'s dispose closure or directly when the proxy
    /// outlives the upstream view-model.
    func dispose() {
        collectionTask?.cancel()
        collectionTask = nil
    }
}

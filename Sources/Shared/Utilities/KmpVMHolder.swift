// MARK: - KmpVMHolder
// RAII wrapper for KMP `BaseViewModel`-derived view models.
// macOS 14+, Swift 5.10

import Foundation

/// RAII wrapper that owns a Kotlin/Multiplatform view-model instance and
/// guarantees its `dispose()` (or equivalent cleanup) is called exactly once
/// when the holder leaves scope.
///
/// ### Why a holder?
/// KMP view models created via `BaseViewModel(parentScope)` carry a Kotlin
/// `CoroutineScope` that must be cancelled when the Swift view disappears.
/// Swift `deinit` is the only deterministic hook we have on the SwiftUI side,
/// so we attach the dispose closure to a reference-typed holder and store it
/// in `@State`.
///
/// ### Usage (SwiftUI)
/// ```swift
/// struct MyScreen: View {
///     @State private var holder: KmpVMHolder<SpecsViewModel> = {
///         let vm = SharedFactory.makeSpecsViewModel(/* deps */)
///         return KmpVMHolder(vm) { vm.dispose() }
///     }()
///
///     var body: some View {
///         // observe via StateFlowProxy, mutate via holder.vm.intent(...)
///     }
/// }
/// ```
///
/// - Important: `dispose` runs on the thread that releases the last strong
///   reference. If your KMP scope must be cancelled on the main thread,
///   wrap the call: `KmpVMHolder(vm) { Task { @MainActor in vm.dispose() } }`.
///   Caveat: the deferred `Task` will run after the holder's `deinit`
///   returns, so do NOT rely on synchronous teardown in that mode.
///
/// - Note: `VM` is constrained to `AnyObject` because KMP-generated Swift
///   bindings are always classes (Objective-C bridging).
final class KmpVMHolder<VM: AnyObject> {
    /// The wrapped KMP view-model instance. Read-only after init.
    let vm: VM

    private let disposer: () -> Void

    /// Create a holder that will invoke `dispose` exactly once on `deinit`.
    ///
    /// - Parameters:
    ///   - vm: The Kotlin view-model instance to retain.
    ///   - dispose: Cleanup closure (typically `{ vm.dispose() }` or
    ///     `{ vm.viewModelScope.cancel() }`).
    init(_ vm: VM, dispose: @escaping () -> Void) {
        self.vm = vm
        self.disposer = dispose
    }

    deinit { disposer() }
}

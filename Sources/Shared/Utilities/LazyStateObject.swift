// MARK: - LazyStateObject
// DynamicProperty wrapper for lazy view-model initialisation.
// macOS 14+, Swift 5.10

import SwiftUI

/// A reference box for a lazily-initialised view model.
///
/// The historical pattern was:
/// ```swift
/// @State private var vm: MyVM?
/// private var viewModel: MyVM {
///     if let v = vm { return v }
///     let created = MyVM(...)
///     Task { @MainActor in vm = created }   // ← anti-pattern
///     return created
/// }
/// ```
/// That violates the SwiftUI invariant "view bodies must be pure" — a body
/// access produces a side-effecting `Task` that mutates `@State`. SwiftUI
/// may also re-evaluate `body` before the deferred mutation lands, briefly
/// constructing two distinct VM instances and racing the observation system.
///
/// `LazyStateObject` solves this by storing the VM inside a reference box
/// managed by `@State`. The factory is called exactly once per view-state
/// lifetime; subsequent body evaluations return the cached value with no
/// side effects.
///
/// **Usage** (env-dependent factory):
/// ```swift
/// struct MyView: View {
///     @Environment(\.gitService) private var gitService
///     @State private var vmBox = LazyStateObject<MyViewModel>()
///
///     private var vm: MyViewModel {
///         vmBox.resolve { MyViewModel(gitService: gitService) }
///     }
///
///     var body: some View {
///         Text(vm.title)
///     }
/// }
/// ```
///
/// The box itself is a `final class`, so `@State` stores it by identity and
/// the box survives across body re-evaluations.
@MainActor
final class LazyStateObject<VM: AnyObject> {

    private(set) var value: VM?

    init() {}

    /// Returns the cached view model, creating it via `factory` on the first
    /// access. Subsequent calls are O(1) and do not invoke the factory.
    func resolve(factory: () -> VM) -> VM {
        if let existing = value { return existing }
        let made = factory()
        value = made
        return made
    }
}

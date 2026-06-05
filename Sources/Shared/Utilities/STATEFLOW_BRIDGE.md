# StateFlow Bridge — Planned Implementation

Status: **NOT YET IMPLEMENTED.** Awaiting `VibeStudioShared.framework` integration
into the Xcode target (project.yml wires the framework; KMP-side BaseViewModel
exposing `StateFlow<UiState>` is being landed in parallel sprints).

## Design (sprint-5)

When `VibeStudioShared.framework` is available and a KMP view-model exposes
state via `StateFlow<UiState>`, SKIE generates a Swift-friendly view of the
flow:

```swift
public var uiState: SkieSwiftStateFlow<UiState> { get }
```

`SkieSwiftStateFlow<T>` conforms to `AsyncSequence`, so consumption is a plain
`for await` loop. The recommended pattern is to keep the loop at the SwiftUI
call site, not hidden inside a helper:

```swift
struct MyScreen: View {
    @State private var proxy = StateFlowProxy<MyState>(initial: .empty)
    @State private var holder: KmpVMHolder<MyViewModel> = ...

    var body: some View {
        ContentView(state: proxy.value, dispatch: holder.vm.dispatch)
            .task {
                // Cancelled automatically when the view disappears.
                for await next in holder.vm.uiState {
                    proxy.update(next)
                }
            }
    }
}
```

## Why no `bind(_:)` helper

A previous draft of `StateFlowProxy` exposed `bind(to: Any)` that spawned an
internal `Task`. That version was removed because:

1. **Type erasure:** taking `Any` forced an unsafe cast that compiled but
   silently produced a no-op when the cast failed.
2. **Cancellation:** an internally-owned `Task` outlives the SwiftUI view
   lifecycle by default; coordinating it with `deinit` is error-prone and
   bypasses structured concurrency.
3. **Retain cycles:** capturing `self` weakly inside a `Task` while holding the
   proxy in `@State` makes the ownership graph hard to follow.

The explicit `for await` at the call site is shorter, safer, and respects
SwiftUI's `.task` lifetime semantics.

## Implementation checklist (when SKIE lands)

- [ ] Add `import VibeStudioShared` to the consumer file.
- [ ] Replace the placeholder `StateFlowProxy<Value>(initial:)` seed with
      `holder.vm.uiState.value` (SKIE exposes `.value` on StateFlow).
- [ ] Verify `MyState` is `Sendable` (KMP-generated classes are bridged as
      `class` and may need `@unchecked Sendable` if marked `@Stable` in Kotlin).
- [ ] Add a unit test that flips state on the KMP side and asserts the proxy's
      published `value` updates on the main actor.

// MARK: - KmpVMHolderTests
// Disposal lifecycle: idempotent dispose, deinit fallback when caller forgets.

import XCTest
@testable import VibeStudio

// MARK: - Test double

/// Records dispose invocations from any thread. Used to verify single-call semantics.
private final class FakeDisposable: Disposable, @unchecked Sendable {
    private let lock = NSLock()
    private var _count = 0

    var disposeCount: Int {
        lock.lock(); defer { lock.unlock() }
        return _count
    }

    func dispose() async {
        lock.lock(); _count += 1; lock.unlock()
    }
}

// MARK: - Tests

final class KmpVMHolderTests: XCTestCase {

    func test_dispose_callsUnderlyingDisposeOnce() async {
        let fake = FakeDisposable()
        let holder = KmpVMHolder(fake)
        await holder.dispose()
        XCTAssertEqual(fake.disposeCount, 1)
    }

    func test_dispose_calledTwice_callsUnderlyingOnce() async {
        let fake = FakeDisposable()
        let holder = KmpVMHolder(fake)
        await holder.dispose()
        await holder.dispose()
        XCTAssertEqual(fake.disposeCount, 1)
    }

    func test_dispose_concurrentCalls_callUnderlyingOnce() async {
        let fake = FakeDisposable()
        let holder = KmpVMHolder(fake)

        await withTaskGroup(of: Void.self) { group in
            for _ in 0..<32 {
                group.addTask { await holder.dispose() }
            }
        }
        XCTAssertEqual(fake.disposeCount, 1)
    }

    func test_deinit_withoutExplicitDispose_eventuallyTriggersFallback() async {
        let fake = FakeDisposable()

        // Wrap in autoreleasepool so the holder is released at scope exit.
        do {
            let holder = KmpVMHolder(fake)
            // Touch the holder so it isn't optimised away.
            _ = holder.vm
        }

        // The deinit dispatches a detached Task; give it time to run.
        // Up to ~2s polling with early exit.
        for _ in 0..<40 {
            if fake.disposeCount >= 1 { break }
            try? await Task.sleep(nanoseconds: 50_000_000)
        }
        XCTAssertEqual(fake.disposeCount, 1, "deinit fallback must dispose the VM exactly once")
    }

    func test_activate_isNoOp_doesNotInvokeDispose() async {
        let fake = FakeDisposable()
        let holder = KmpVMHolder(fake)
        await holder.activate()
        XCTAssertEqual(fake.disposeCount, 0)
    }
}

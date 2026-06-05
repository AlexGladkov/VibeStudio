// MARK: - ContinuationHolderTests
// Asserts single-resume semantics, idempotency under concurrent resume calls,
// and that a holder created without a continuation is safe to call resume() on.

import XCTest
@testable import VibeStudio

final class ContinuationHolderTests: XCTestCase {

    // MARK: - Basic

    func test_resume_resumesAwaitingContinuation() async {
        let holder = ContinuationHolder()
        let exp = expectation(description: "resumed")

        Task {
            await withCheckedContinuation { (c: CheckedContinuation<Void, Never>) in
                holder.set(c)
                // Resume asynchronously from another task.
                Task { holder.resume() }
            }
            exp.fulfill()
        }
        await fulfillment(of: [exp], timeout: 2.0)
    }

    // MARK: - Idempotency

    func test_resume_calledTwiceSequentially_isSafe() async {
        let holder = ContinuationHolder()
        await withCheckedContinuation { (c: CheckedContinuation<Void, Never>) in
            holder.set(c)
            holder.resume()
            // Second call MUST NOT crash with "continuation already resumed".
            holder.resume()
        }
    }

    func test_resume_calledBeforeSet_isNoOp() {
        let holder = ContinuationHolder()
        // Should not crash — there is no continuation to resume yet.
        holder.resume()
    }

    // MARK: - Concurrency

    func test_resume_concurrentCalls_resumeExactlyOnce() async {
        // Repeat many times to catch any race the NSLock might fail to cover.
        for _ in 0..<200 {
            let holder = ContinuationHolder()
            await withCheckedContinuation { (c: CheckedContinuation<Void, Never>) in
                holder.set(c)
                DispatchQueue.concurrentPerform(iterations: 16) { _ in
                    holder.resume()
                }
            }
        }
    }
}

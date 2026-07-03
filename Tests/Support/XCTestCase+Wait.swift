import XCTest

/// Deterministic replacements for fixed `Task.sleep` delays in async tests.
///
/// Instead of sleeping for a hard-coded duration and hoping the work finished,
/// these helpers poll a condition on a short interval until it becomes `true`
/// or a timeout elapses. This makes timing-dependent tests both faster (they
/// return as soon as the condition holds) and more stable (no flakiness from
/// under-provisioned fixed delays on a busy CI machine).
@MainActor
extension XCTestCase {

    /// Polls `condition` until it returns `true` or `timeout` elapses.
    ///
    /// - Parameters:
    ///   - timeout: Maximum time to wait before giving up. Default `2` seconds.
    ///   - pollInterval: Delay between successive `condition` evaluations.
    ///   - condition: The predicate to satisfy. Re-evaluated on every poll.
    /// - Returns: `true` if `condition` became `true` within `timeout`,
    ///   otherwise `false`.
    @discardableResult
    func waitUntil(
        timeout: TimeInterval = 2.0,
        pollInterval: TimeInterval = 0.01,
        _ condition: @escaping () -> Bool
    ) async -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if condition() { return true }
            try? await Task.sleep(for: .seconds(pollInterval))
        }
        // Final evaluation after the deadline in case the last sleep overshot.
        return condition()
    }

    /// Asserts that `condition` becomes `true` within `timeout`, failing the
    /// test otherwise.
    ///
    /// Convenience wrapper over ``waitUntil(timeout:pollInterval:_:)`` for the
    /// common case where a timeout should be treated as a hard failure.
    func waitForCondition(
        timeout: TimeInterval = 2.0,
        pollInterval: TimeInterval = 0.01,
        _ message: @autoclosure @escaping () -> String = "Condition not met within timeout",
        file: StaticString = #filePath,
        line: UInt = #line,
        _ condition: @escaping () -> Bool
    ) async {
        let met = await waitUntil(timeout: timeout, pollInterval: pollInterval, condition)
        XCTAssertTrue(met, message(), file: file, line: line)
    }
}

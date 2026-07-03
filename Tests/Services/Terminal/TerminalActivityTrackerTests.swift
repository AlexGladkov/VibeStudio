import XCTest
@testable import VibeStudio

@MainActor
final class TerminalActivityTrackerTests: XCTestCase {

    private var stateChanges: [(UUID, TabActivityState)] = []
    private var sut: TerminalActivityTracker!

    override func setUp() {
        super.setUp()
        stateChanges = []
        sut = TerminalActivityTracker { [unowned self] projectId, state in
            self.stateChanges.append((projectId, state))
        }
    }

    override func tearDown() {
        sut = nil
        stateChanges = []
        super.tearDown()
    }

    // MARK: - Helpers

    private let projectA = UUID()
    private let sessionA = UUID()

    /// A prompt checker that always returns the given value.
    private func promptChecker(_ result: Bool) -> @MainActor (UUID) -> Bool {
        { _ in result }
    }

    // MARK: - handleActivity

    func testHandleActivity_setsRunningState() {
        let processed = sut.handleActivity(
            sessionId: sessionA,
            projectId: projectA,
            currentState: .idle,
            promptChecker: promptChecker(false)
        )

        XCTAssertTrue(processed)
        XCTAssertEqual(stateChanges.count, 1)
        XCTAssertEqual(stateChanges.first?.0, projectA)
        XCTAssertEqual(stateChanges.first?.1, .running)
    }

    func testHandleActivity_debounce_ignoresRapidEvents() {
        // First event processed.
        let first = sut.handleActivity(
            sessionId: sessionA,
            projectId: projectA,
            currentState: .idle,
            promptChecker: promptChecker(false)
        )
        XCTAssertTrue(first)

        // Immediate second event debounced.
        let second = sut.handleActivity(
            sessionId: sessionA,
            projectId: projectA,
            currentState: .running,
            promptChecker: promptChecker(false)
        )
        XCTAssertFalse(second)
        XCTAssertEqual(stateChanges.count, 1, "Second event should be debounced")
    }

    // MARK: - Idle Timer

    /// Short idle timeout so the timer fires quickly; the test polls for the
    /// resulting state transition instead of sleeping for a fixed duration.
    private static let testIdleTimeout: TimeInterval = 0.05

    func testIdleTimer_firesAfterTimeout_promptAtShell_setsIdle() async throws {
        sut = TerminalActivityTracker(idleTimeout: Self.testIdleTimeout) { [unowned self] projectId, state in
            self.stateChanges.append((projectId, state))
        }

        _ = sut.handleActivity(
            sessionId: sessionA,
            projectId: projectA,
            currentState: .idle,
            promptChecker: promptChecker(true)  // At prompt
        )

        // Poll until the idle timer fires and transitions to .idle.
        let fired = await waitUntil { self.stateChanges.map(\.1).last == .idle }
        XCTAssertTrue(fired, "Idle timer should transition to .idle")

        // Should have: .running (from handleActivity) + .idle (from timer).
        let states = stateChanges.map(\.1)
        XCTAssertTrue(states.contains(.running))
        XCTAssertEqual(states.last, .idle)
    }

    func testIdleTimer_firesAfterTimeout_notAtPrompt_setsWaitingForInput() async throws {
        sut = TerminalActivityTracker(idleTimeout: Self.testIdleTimeout) { [unowned self] projectId, state in
            self.stateChanges.append((projectId, state))
        }

        _ = sut.handleActivity(
            sessionId: sessionA,
            projectId: projectA,
            currentState: .idle,
            promptChecker: promptChecker(false)  // Not at prompt
        )

        let fired = await waitUntil { self.stateChanges.map(\.1).last == .waitingForInput }
        XCTAssertTrue(fired, "Idle timer should transition to .waitingForInput")

        let states = stateChanges.map(\.1)
        XCTAssertTrue(states.contains(.running))
        XCTAssertEqual(states.last, .waitingForInput)
    }

    func testIdleTimer_cancelledByNewActivity() async throws {
        sut = TerminalActivityTracker(idleTimeout: Self.testIdleTimeout) { [unowned self] projectId, state in
            self.stateChanges.append((projectId, state))
        }

        // Fire first activity — schedules timer A for projectA.
        _ = sut.handleActivity(
            sessionId: sessionA,
            projectId: projectA,
            currentState: .idle,
            promptChecker: promptChecker(true)
        )

        // Immediately fire a second activity for the SAME project via a
        // different session (bypasses per-session debounce). This must cancel
        // timer A and replace it with timer B.
        let sessionB = UUID()
        _ = sut.handleActivity(
            sessionId: sessionB,
            projectId: projectA,
            currentState: .running,
            promptChecker: promptChecker(true)
        )

        // Wait until a non-running transition appears (timer B firing).
        let fired = await waitUntil { self.stateChanges.contains { $0.1 != .running } }
        XCTAssertTrue(fired, "The surviving timer should fire")

        // Settle margin: if timer A had NOT been cancelled it fires around the
        // same time as timer B, producing a SECOND non-running state.
        try await Task.sleep(for: .seconds(Self.testIdleTimeout * 3))

        let nonRunning = stateChanges.filter { $0.1 != .running }
        XCTAssertEqual(nonRunning.count, 1,
                       "Cancelled first timer must not fire — exactly one transition expected")
    }

    // MARK: - markProjectSeen

    func testMarkProjectSeen_clearsWaitingForInput() {
        sut.markProjectSeen(projectA, currentState: .waitingForInput)

        XCTAssertEqual(stateChanges.count, 1)
        XCTAssertEqual(stateChanges.first?.1, .idle)
    }

    func testMarkProjectSeen_idleState_noStateChange() {
        sut.markProjectSeen(projectA, currentState: .idle)
        XCTAssertTrue(stateChanges.isEmpty)
    }

    // MARK: - handleProcessExit

    func testHandleProcessExit_zeroCode_setsIdle() {
        sut.handleProcessExit(projectId: projectA, exitCode: 0)
        XCTAssertEqual(stateChanges.first?.1, .idle)
    }

    func testHandleProcessExit_nonZeroCode_setsError() {
        sut.handleProcessExit(projectId: projectA, exitCode: 1)
        XCTAssertEqual(stateChanges.first?.1, .error)
    }

    // MARK: - removeProject

    func testRemoveProject_cancelsTimerAndSetsIdle() {
        // Start activity to create a timer.
        _ = sut.handleActivity(
            sessionId: sessionA,
            projectId: projectA,
            currentState: .idle,
            promptChecker: promptChecker(false)
        )
        stateChanges.removeAll()

        sut.removeProject(projectA)

        XCTAssertEqual(stateChanges.count, 1)
        XCTAssertEqual(stateChanges.first?.1, .idle)
    }

    // MARK: - removeSession

    func testRemoveSession_clearsDebounceTracking() {
        // Activity creates debounce entry.
        _ = sut.handleActivity(
            sessionId: sessionA,
            projectId: projectA,
            currentState: .idle,
            promptChecker: promptChecker(false)
        )

        sut.removeSession(sessionA)

        // After removal, same session should NOT be debounced.
        stateChanges.removeAll()
        let processed = sut.handleActivity(
            sessionId: sessionA,
            projectId: projectA,
            currentState: .running,
            promptChecker: promptChecker(false)
        )
        XCTAssertTrue(processed, "After removeSession, debounce state should be cleared")
    }
}

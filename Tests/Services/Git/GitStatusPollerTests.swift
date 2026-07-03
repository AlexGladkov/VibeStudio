import XCTest
@testable import VibeStudio

/// Tests for GitStatusPoller backoff logic.
///
/// Exercises the REAL `effectiveInterval(isActive:consecutiveErrors:)` method
/// on a live `GitStatusPoller` instance — no replicated formula. Since that
/// pure overload is the single source of truth (the production
/// `effectiveInterval(isActive:)` delegates to it), these assertions verify the
/// exact code path used at runtime.
@MainActor
final class GitStatusPollerTests: XCTestCase {

    // Expected boundary constants (documented in GitStatusPoller).
    private let maxBackoffInterval: TimeInterval = 30

    private func makeSUT() -> GitStatusPoller {
        GitStatusPoller(gitService: MockGitService())
    }

    /// Invoke the production backoff formula directly.
    private func computeEffectiveInterval(
        isActive: Bool,
        consecutiveErrors: Int
    ) -> TimeInterval {
        makeSUT().effectiveInterval(isActive: isActive, consecutiveErrors: consecutiveErrors)
    }

    // MARK: - Active Project (base = 3s)

    func testActiveNoErrorsUsesBaseInterval() {
        let interval = computeEffectiveInterval(isActive: true, consecutiveErrors: 0)
        XCTAssertEqual(interval, 3.0, accuracy: 0.001)
    }

    func testActiveOneErrorDoublesInterval() {
        // base * 2^1 = 3 * 2 = 6
        let interval = computeEffectiveInterval(isActive: true, consecutiveErrors: 1)
        XCTAssertEqual(interval, 6.0, accuracy: 0.001)
    }

    func testActiveTwoErrorsQuadruplesInterval() {
        // base * 2^2 = 3 * 4 = 12
        let interval = computeEffectiveInterval(isActive: true, consecutiveErrors: 2)
        XCTAssertEqual(interval, 12.0, accuracy: 0.001)
    }

    func testActiveThreeErrorsEightXInterval() {
        // base * 2^3 = 3 * 8 = 24
        let interval = computeEffectiveInterval(isActive: true, consecutiveErrors: 3)
        XCTAssertEqual(interval, 24.0, accuracy: 0.001)
    }

    func testActiveFourErrorsSixteenXCappedAt30() {
        // base * 2^4 = 3 * 16 = 48, capped at 30
        let interval = computeEffectiveInterval(isActive: true, consecutiveErrors: 4)
        XCTAssertEqual(interval, 30.0, accuracy: 0.001)
    }

    func testActiveFiveErrorsStillCappedAt30() {
        // min(consecutiveErrors, 4) = 4, so base * 2^4 = 48, capped at 30
        let interval = computeEffectiveInterval(isActive: true, consecutiveErrors: 5)
        XCTAssertEqual(interval, 30.0, accuracy: 0.001)
    }

    func testActiveHundredErrorsStillCappedAt30() {
        let interval = computeEffectiveInterval(isActive: true, consecutiveErrors: 100)
        XCTAssertEqual(interval, 30.0, accuracy: 0.001)
    }

    // MARK: - Background Project (base = 30s)

    func testBackgroundNoErrorsUsesBackgroundInterval() {
        let interval = computeEffectiveInterval(isActive: false, consecutiveErrors: 0)
        XCTAssertEqual(interval, 30.0, accuracy: 0.001)
    }

    func testBackgroundOneErrorCappedAt30() {
        // base * 2^1 = 30 * 2 = 60, capped at 30
        let interval = computeEffectiveInterval(isActive: false, consecutiveErrors: 1)
        XCTAssertEqual(interval, 30.0, accuracy: 0.001)
    }

    func testBackgroundMultipleErrorsAlwaysCappedAt30() {
        // Background base (30) already >= maxBackoff (30), so any backoff
        // is always capped at 30.
        for errors in 0...10 {
            let interval = computeEffectiveInterval(isActive: false, consecutiveErrors: errors)
            XCTAssertEqual(interval, 30.0, accuracy: 0.001,
                           "Expected 30.0 for \(errors) consecutive errors in background mode")
        }
    }

    // MARK: - Backoff Formula Properties

    func testBackoffIsMonotonicallyIncreasingForActive() {
        var previous: TimeInterval = 0
        for errors in 0...4 {
            let interval = computeEffectiveInterval(isActive: true, consecutiveErrors: errors)
            XCTAssertGreaterThanOrEqual(interval, previous,
                "Interval should increase or stay same with more errors. " +
                "errors=\(errors), interval=\(interval), previous=\(previous)")
            previous = interval
        }
    }

    func testBackoffNeverExceedsMaximum() {
        for errors in 0...20 {
            let activeInterval = computeEffectiveInterval(isActive: true, consecutiveErrors: errors)
            let bgInterval = computeEffectiveInterval(isActive: false, consecutiveErrors: errors)
            XCTAssertLessThanOrEqual(activeInterval, maxBackoffInterval,
                "Active interval should not exceed max for \(errors) errors")
            XCTAssertLessThanOrEqual(bgInterval, maxBackoffInterval,
                "Background interval should not exceed max for \(errors) errors")
        }
    }

    // MARK: - GitStatus.empty

    func testGitStatusEmptyIsClean() {
        let status = GitStatus.empty
        XCTAssertEqual(status.branch, "")
        XCTAssertEqual(status.aheadCount, 0)
        XCTAssertEqual(status.behindCount, 0)
        XCTAssertTrue(status.stagedFiles.isEmpty)
        XCTAssertTrue(status.unstagedFiles.isEmpty)
        XCTAssertTrue(status.untrackedFiles.isEmpty)
        XCTAssertTrue(status.isClean)
    }

    func testGitStatusIsCleanFalseWhenStaged() {
        let status = GitStatus(
            branch: "main",
            aheadCount: 0,
            behindCount: 0,
            stagedFiles: [GitFile(path: "a.swift", status: .modified)],
            unstagedFiles: [],
            untrackedFiles: []
        )
        XCTAssertFalse(status.isClean)
    }

    func testGitStatusIsCleanFalseWhenUntracked() {
        let status = GitStatus(
            branch: "main",
            aheadCount: 0,
            behindCount: 0,
            stagedFiles: [],
            unstagedFiles: [],
            untrackedFiles: [GitFile(path: "new.txt", status: .untracked)]
        )
        XCTAssertFalse(status.isClean)
    }
}

import XCTest
@testable import VibeStudio

@MainActor
final class GitStatusPollerDeinitTests: XCTestCase {

    // MARK: - Deinit Cancellation

    func testDeinit_cancelsBothTasks() async throws {
        var sut: GitStatusPoller? = GitStatusPoller(gitService: MockGitService())
        weak var weakSut = sut

        // Start polling to create pollingTask.
        sut?.startPolling(for: URL(fileURLWithPath: "/tmp/repo"))

        // Trigger refreshNow to create refreshTask.
        sut?.refreshNow()

        sut = nil

        // Poll until deallocated — both tasks capture [weak self], so dropping
        // the last strong reference must run deinit and null the weak ref.
        let deallocated = await waitUntil { weakSut == nil }
        XCTAssertTrue(deallocated, "GitStatusPoller should be deallocated — no task retain cycle")
    }

    func testRefreshNow_cancelsPreviousTask() async throws {
        let sut = GitStatusPoller(gitService: MockGitService())
        sut.startPolling(for: URL(fileURLWithPath: "/tmp/repo"))

        // Rapid refreshNow calls should not accumulate tasks. The implementation
        // cancels the prior refreshTask before creating a new one, so only one
        // poll is ever in-flight.
        for _ in 0..<10 {
            sut.refreshNow()
        }

        // Wait until the in-flight poll settles (no crash / no accumulation),
        // then stop. Deterministic: polls complete once isPolling drops to false.
        let settled = await waitUntil { !sut.isPolling }
        XCTAssertTrue(settled, "Coalesced refresh should settle without hanging")
        sut.stopPolling()
    }

    func testStopPolling_cancelsPollingTask() async throws {
        let sut = GitStatusPoller(gitService: MockGitService())
        sut.startPolling(for: URL(fileURLWithPath: "/tmp/repo"))

        sut.stopPolling()

        // After stop, isPolling must settle to false (any in-flight poll clears
        // it in its defer block).
        let stopped = await waitUntil { !sut.isPolling }
        XCTAssertTrue(stopped, "isPolling should be false after stopPolling")
    }
}

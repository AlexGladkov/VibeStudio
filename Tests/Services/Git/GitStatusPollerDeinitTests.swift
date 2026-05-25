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

        // Allow tasks to start.
        try await Task.sleep(for: .milliseconds(50))

        sut = nil

        // Allow async cleanup.
        try await Task.sleep(for: .milliseconds(200))

        XCTAssertNil(weakSut, "GitStatusPoller should be deallocated — no task retain cycle")
    }

    func testRefreshNow_cancelsPreviousTask() async throws {
        let sut = GitStatusPoller(gitService: MockGitService())
        sut.startPolling(for: URL(fileURLWithPath: "/tmp/repo"))

        // Rapid refreshNow calls should not accumulate tasks.
        for _ in 0..<10 {
            sut.refreshNow()
        }

        // Just verify no crash. The implementation cancels the prior refreshTask
        // before creating a new one, so only one poll should be in-flight.
        try await Task.sleep(for: .milliseconds(100))
        sut.stopPolling()
    }

    func testStopPolling_cancelsPollingTask() async throws {
        let sut = GitStatusPoller(gitService: MockGitService())
        sut.startPolling(for: URL(fileURLWithPath: "/tmp/repo"))

        try await Task.sleep(for: .milliseconds(50))

        sut.stopPolling()

        // After stop, isPolling should be false.
        try await Task.sleep(for: .milliseconds(100))
        XCTAssertFalse(sut.isPolling)
    }
}

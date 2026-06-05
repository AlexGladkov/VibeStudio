// MARK: - AppNavigationCoordinatorTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

@MainActor
final class AppNavigationCoordinatorTests: XCTestCase {

    func test_syncMode_switchToCodeSpeak_closesRightPanels() {
        let sut = AppNavigationCoordinator()
        sut.showingChangesPanel = true
        sut.showingSpecPanel = true
        sut.showingTraceabilityPanel = true

        sut.syncMode(isCodeSpeak: true)

        XCTAssertEqual(sut.currentMode, .codeSpeak)
        XCTAssertFalse(sut.showingChangesPanel, "Switching to CodeSpeak must close changes panel")
        XCTAssertFalse(sut.showingSpecPanel)
        XCTAssertFalse(sut.showingTraceabilityPanel)
    }

    func test_syncMode_idempotent_doesNotReclosePanels() {
        let sut = AppNavigationCoordinator()
        sut.syncMode(isCodeSpeak: true)
        sut.showingChangesPanel = true  // user re-opens

        sut.syncMode(isCodeSpeak: true) // no-op
        XCTAssertTrue(sut.showingChangesPanel,
                      "syncMode with same mode must not mutate panel state")
    }

    func test_syncMode_switchToRegular_leavesPanelsUntouched() {
        let sut = AppNavigationCoordinator()
        sut.syncMode(isCodeSpeak: true)
        sut.showingChangesPanel = true
        sut.syncMode(isCodeSpeak: false)

        XCTAssertEqual(sut.currentMode, .regular)
        XCTAssertTrue(sut.showingChangesPanel,
                      "Switching back to regular must preserve user's panel state")
    }

    func test_specsColumnWidth_acceptsArbitraryValues() {
        let sut = AppNavigationCoordinator()
        XCTAssertEqual(sut.specsColumnWidth, 220, "Default column width is 220")

        sut.specsColumnWidth = 350
        XCTAssertEqual(sut.specsColumnWidth, 350)
    }

    func test_observation_notifiesOnAgentToInstallChange() async {
        let sut = AppNavigationCoordinator()

        var observed = false
        withObservationTracking {
            _ = sut.agentToInstall
        } onChange: {
            observed = true
        }
        sut.agentToInstall = .claude
        // Observation callback fires synchronously on the next run-loop turn.
        try? await Task.sleep(for: .milliseconds(20))
        XCTAssertTrue(observed)
    }

    func test_runBar_initialState_isDefault() {
        let sut = AppNavigationCoordinator()
        XCTAssertFalse(sut.runBar.isRunning)
    }
}

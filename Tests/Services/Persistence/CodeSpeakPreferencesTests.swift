// MARK: - CodeSpeakPreferencesTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

@MainActor
final class CodeSpeakPreferencesTests: XCTestCase {

    private let keys = [
        "cs_auto_build_on_save",
        "cs_build_on_open",
        "cs_auto_open_panel",
        "cs_default_command",
        "cs_notify_on_complete",
        "cs_show_failing_only",
    ]

    override func setUp() {
        super.setUp()
        keys.forEach { UserDefaults.standard.removeObject(forKey: $0) }
    }

    override func tearDown() {
        keys.forEach { UserDefaults.standard.removeObject(forKey: $0) }
        super.tearDown()
    }

    func test_init_firstLaunch_defaults() {
        let sut = CodeSpeakPreferences()
        XCTAssertFalse(sut.autoBuildOnSave)
        XCTAssertFalse(sut.buildOnProjectOpen)
        XCTAssertTrue(sut.autoOpenBuildPanel, "autoOpenBuildPanel must default to TRUE on first launch")
        XCTAssertEqual(sut.defaultCommand, .build)
        XCTAssertFalse(sut.notifyOnComplete)
        XCTAssertFalse(sut.showFailingOnly)
    }

    func test_autoOpenBuildPanel_persistsExplicitFalse() {
        // Explicitly set to false, then re-init — should remain false (not regress to true).
        UserDefaults.standard.set(false, forKey: "cs_auto_open_panel")
        let sut = CodeSpeakPreferences()
        XCTAssertFalse(sut.autoOpenBuildPanel)
    }

    func test_setters_persistToUserDefaults() {
        let sut = CodeSpeakPreferences()
        sut.autoBuildOnSave = true
        sut.buildOnProjectOpen = true
        sut.autoOpenBuildPanel = false
        sut.defaultCommand = .test
        sut.showFailingOnly = true

        XCTAssertTrue(UserDefaults.standard.bool(forKey: "cs_auto_build_on_save"))
        XCTAssertTrue(UserDefaults.standard.bool(forKey: "cs_build_on_open"))
        XCTAssertFalse(UserDefaults.standard.bool(forKey: "cs_auto_open_panel"))
        XCTAssertEqual(UserDefaults.standard.string(forKey: "cs_default_command"), "test")
        XCTAssertTrue(UserDefaults.standard.bool(forKey: "cs_show_failing_only"))

        // Reload
        let reloaded = CodeSpeakPreferences()
        XCTAssertTrue(reloaded.autoBuildOnSave)
        XCTAssertFalse(reloaded.autoOpenBuildPanel)
        XCTAssertEqual(reloaded.defaultCommand, .test)
        XCTAssertTrue(reloaded.showFailingOnly)
    }

    func test_defaultCommand_invalidRawValue_fallsBackToBuild() {
        UserDefaults.standard.set("nonsense-command", forKey: "cs_default_command")
        let sut = CodeSpeakPreferences()
        XCTAssertEqual(sut.defaultCommand, .build)
    }

    func test_sendCompletionNotification_doesNotCrashWhenDisabled() {
        let sut = CodeSpeakPreferences()
        sut.notifyOnComplete = false
        // No-op path; just ensure it doesn't crash.
        sut.sendCompletionNotification(command: .build, exitCode: 0, wasCancelled: false)
    }

    func test_sendCompletionNotification_cancelledIsNoOp() {
        let sut = CodeSpeakPreferences()
        sut.sendCompletionNotification(command: .build, exitCode: 0, wasCancelled: true)
        // Cannot easily assert delivery without UN center mocking — smoke test only.
    }
}

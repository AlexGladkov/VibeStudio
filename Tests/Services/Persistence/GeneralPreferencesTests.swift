// MARK: - GeneralPreferencesTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

@MainActor
final class GeneralPreferencesTests: XCTestCase {

    private let keys = [
        "vs_confirm_tab_close",
        "vs_terminal_font_size",
        "vs_claude_skip_permissions",
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
        let sut = GeneralPreferences()
        XCTAssertTrue(sut.confirmTabClose, "confirmTabClose must default to TRUE on first launch")
        XCTAssertEqual(sut.terminalFontSize, 13)
        XCTAssertFalse(sut.claudeSkipPermissions)
    }

    func test_confirmTabClose_persistsExplicitFalse() {
        UserDefaults.standard.set(false, forKey: "vs_confirm_tab_close")
        let sut = GeneralPreferences()
        XCTAssertFalse(sut.confirmTabClose)
    }

    func test_terminalFontSize_clampsToValidRange() {
        let sut = GeneralPreferences()

        sut.terminalFontSize = 4       // below min
        XCTAssertEqual(sut.terminalFontSize, 9)

        sut.terminalFontSize = 99      // above max
        XCTAssertEqual(sut.terminalFontSize, 24)

        sut.terminalFontSize = 15      // valid
        XCTAssertEqual(sut.terminalFontSize, 15)
    }

    func test_persistence_roundtrip() {
        let sut = GeneralPreferences()
        sut.confirmTabClose = false
        sut.terminalFontSize = 18
        sut.claudeSkipPermissions = true

        let reloaded = GeneralPreferences()
        XCTAssertFalse(reloaded.confirmTabClose)
        XCTAssertEqual(reloaded.terminalFontSize, 18)
        XCTAssertTrue(reloaded.claudeSkipPermissions)
    }
}

// MARK: - ThemeServiceTests
// macOS 14+, Swift 5.10

import AppKit
import XCTest
@testable import VibeStudio

@MainActor
final class ThemeServiceTests: XCTestCase {

    private let storageKey = "vs_appearance"

    override func setUp() {
        super.setUp()
        UserDefaults.standard.removeObject(forKey: storageKey)
    }

    override func tearDown() {
        UserDefaults.standard.removeObject(forKey: storageKey)
        NSApp.appearance = nil
        super.tearDown()
    }

    // MARK: - init reads stored value

    func test_init_readsStoredAppearance_dark() {
        UserDefaults.standard.set(1, forKey: storageKey) // 1 = dark
        let sut = ThemeService()
        XCTAssertEqual(sut.selectedAppearance, .dark)
    }

    func test_init_readsStoredAppearance_light() {
        UserDefaults.standard.set(2, forKey: storageKey) // 2 = light
        let sut = ThemeService()
        XCTAssertEqual(sut.selectedAppearance, .light)
    }

    func test_init_unknownRawValue_fallsBackToSystem() {
        UserDefaults.standard.set(99, forKey: storageKey)
        let sut = ThemeService()
        XCTAssertEqual(sut.selectedAppearance, .system)
    }

    // MARK: - applyStoredAppearance writes NSApp.appearance

    func test_applyStoredAppearance_dark_setsDarkAquaOnNSApp() {
        UserDefaults.standard.set(1, forKey: storageKey)
        let sut = ThemeService()
        sut.applyStoredAppearance()
        XCTAssertEqual(NSApp.appearance?.name, .darkAqua)
    }

    func test_applyStoredAppearance_light_setsAquaOnNSApp() {
        UserDefaults.standard.set(2, forKey: storageKey)
        let sut = ThemeService()
        sut.applyStoredAppearance()
        XCTAssertEqual(NSApp.appearance?.name, .aqua)
    }

    func test_applyStoredAppearance_system_clearsNSAppAppearance() {
        UserDefaults.standard.set(0, forKey: storageKey)
        NSApp.appearance = NSAppearance(named: .darkAqua) // leftover
        let sut = ThemeService()
        sut.applyStoredAppearance()
        XCTAssertNil(NSApp.appearance, "system mode must clear NSApp.appearance")
    }

    // MARK: - setAppearance persists value + applies

    func test_setAppearance_persistsRawValue() {
        let sut = ThemeService()
        sut.setAppearance(.dark)
        XCTAssertEqual(UserDefaults.standard.integer(forKey: storageKey), AppAppearance.dark.rawValue)
        XCTAssertEqual(sut.selectedAppearance, .dark)
        XCTAssertEqual(NSApp.appearance?.name, .darkAqua)
    }

    func test_setAppearance_switchToLight_updatesNSApp() {
        let sut = ThemeService()
        sut.setAppearance(.dark)
        sut.setAppearance(.light)
        XCTAssertEqual(NSApp.appearance?.name, .aqua)
    }

    // MARK: - resolvedColorScheme

    func test_resolvedColorScheme_darkExplicit() {
        let sut = ThemeService()
        sut.setAppearance(.dark)
        XCTAssertEqual(sut.resolvedColorScheme, .dark)
    }

    func test_resolvedColorScheme_lightExplicit() {
        let sut = ThemeService()
        sut.setAppearance(.light)
        XCTAssertEqual(sut.resolvedColorScheme, .light)
    }
}

// MARK: - FreeTabStoreTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

@MainActor
final class FreeTabStoreTests: XCTestCase {

    func test_createFreeTab_assignsAutoNumberedTitles() {
        let sut = FreeTabStore()
        let t1 = sut.createFreeTab()
        let t2 = sut.createFreeTab()
        let t3 = sut.createFreeTab()

        XCTAssertEqual(t1.title, "Terminal")
        XCTAssertEqual(t2.title, "Terminal 2")
        XCTAssertEqual(t3.title, "Terminal 3")
        XCTAssertEqual(sut.freeTabs.count, 3)
    }

    func test_removeFreeTab_removesById() {
        let sut = FreeTabStore()
        let a = sut.createFreeTab()
        let b = sut.createFreeTab()

        sut.removeFreeTab(a.id)
        XCTAssertEqual(sut.freeTabs.count, 1)
        XCTAssertEqual(sut.freeTabs.first?.id, b.id)
    }

    func test_removeFreeTab_unknownIdIsNoOp() {
        let sut = FreeTabStore()
        sut.createFreeTab()
        sut.removeFreeTab(UUID())
        XCTAssertEqual(sut.freeTabs.count, 1)
    }

    func test_isFreeTab_returnsCorrectMembership() {
        let sut = FreeTabStore()
        let a = sut.createFreeTab()
        XCTAssertTrue(sut.isFreeTab(a.id))
        XCTAssertFalse(sut.isFreeTab(UUID()))
    }

    func test_nextActiveId_prefersAnotherFreeTab() {
        let sut = FreeTabStore()
        let a = sut.createFreeTab()
        let b = sut.createFreeTab()
        let project = Project(name: "p", path: URL(fileURLWithPath: "/tmp/p"))

        let next = sut.nextActiveId(after: a.id, projects: [project])
        XCTAssertEqual(next, b.id, "Should pick remaining free tab, not project")
    }

    func test_nextActiveId_fallsBackToFirstProject() {
        let sut = FreeTabStore()
        let a = sut.createFreeTab()
        let project = Project(name: "p", path: URL(fileURLWithPath: "/tmp/p"))

        let next = sut.nextActiveId(after: a.id, projects: [project])
        XCTAssertEqual(next, project.id)
    }

    func test_nextActiveId_returnsNilWhenNothingLeft() {
        let sut = FreeTabStore()
        let a = sut.createFreeTab()
        XCTAssertNil(sut.nextActiveId(after: a.id, projects: []))
    }

    func test_moveFreeTabs_reorders() {
        let sut = FreeTabStore()
        let a = sut.createFreeTab()
        let b = sut.createFreeTab()
        let c = sut.createFreeTab()

        sut.moveFreeTabs(from: IndexSet(integer: 0), to: 3)
        XCTAssertEqual(sut.freeTabs.map { $0.id }, [b.id, c.id, a.id])
    }
}

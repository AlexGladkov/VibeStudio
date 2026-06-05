// MARK: - GitRemoteSetupViewModelTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

@MainActor
final class GitRemoteSetupViewModelTests: XCTestCase {

    private func makeProject() -> Project {
        Project(name: "demo", path: URL(fileURLWithPath: "/tmp/demo"))
    }

    func test_canAdd_falseWhenURLBlank() {
        let sut = GitRemoteSetupViewModel(gitService: MockGitService(), project: makeProject())
        XCTAssertFalse(sut.canAdd)
        sut.remoteUrl = "   "
        XCTAssertFalse(sut.canAdd)
    }

    func test_canAdd_trueWhenURLProvided() {
        let sut = GitRemoteSetupViewModel(gitService: MockGitService(), project: makeProject())
        sut.remoteUrl = "https://github.com/x/y.git"
        XCTAssertTrue(sut.canAdd)
    }

    func test_addRemote_emptyURL_returnsFalse() async {
        let git = MockGitService()
        let sut = GitRemoteSetupViewModel(gitService: git, project: makeProject())
        let dismissed = await sut.addRemote()
        XCTAssertFalse(dismissed)
        let calls = await git.addRemoteCallCount
        XCTAssertEqual(calls, 0)
    }

    func test_addRemote_happyPath_succeeds() async {
        let git = MockGitService()
        let sut = GitRemoteSetupViewModel(gitService: git, project: makeProject())
        sut.remoteUrl = "https://github.com/x/y.git"
        sut.remoteName = "upstream"

        let dismissed = await sut.addRemote()
        XCTAssertTrue(dismissed)
        let calls = await git.addRemoteCallCount
        XCTAssertEqual(calls, 1)
        XCTAssertNotNil(sut.successMessage)
        XCTAssertNil(sut.errorMessage)
    }

    func test_addRemote_emptyNameDefaultsToOrigin() async {
        let git = MockGitService()
        let sut = GitRemoteSetupViewModel(gitService: git, project: makeProject())
        sut.remoteUrl = "https://github.com/x/y.git"
        sut.remoteName = "   "

        let dismissed = await sut.addRemote()
        XCTAssertTrue(dismissed)
        XCTAssertEqual(sut.successMessage, "Remote 'origin' added")
    }
}

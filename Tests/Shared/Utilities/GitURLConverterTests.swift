import XCTest
@testable import VibeStudio

/// Unit tests for ``GitURLConverter.browserURL(from:)`` — pure string→URL
/// conversion for git remotes (SCP, SSH, git://, HTTPS) plus credential
/// stripping. No network, FS, or channel state.
///
/// Documented actual (non-obvious) behaviours:
/// - The credential-strip heuristic keeps `user@` when the part before `@`
///   contains a dot (treated as part of a hostname, not a credential).
/// - A double `@` in SCP form collapses to an empty credential which *is*
///   stripped (empty string has no dot), yielding a clean host URL.
final class GitURLConverterTests: XCTestCase {

    // MARK: - Helper

    private func absString(_ raw: String) -> String? {
        GitURLConverter.browserURL(from: raw)?.absoluteString
    }

    // MARK: - SCP form

    func testSCPFormGitHub() {
        XCTAssertEqual(absString("git@github.com:user/repo.git"),
                       "https://github.com/user/repo")
    }

    func testSCPFormStripsGitSuffixAndCredential() {
        // token@host:path form — credential `git` has no dot → stripped.
        XCTAssertEqual(absString("git@gitlab.com:group/sub/proj.git"),
                       "https://gitlab.com/group/sub/proj")
    }

    // MARK: - ssh:// form

    func testSSHFormStripsUserAndGit() {
        XCTAssertEqual(absString("ssh://git@host/u/r.git"),
                       "https://host/u/r")
    }

    func testSSHFormWithPortPreservesPort() {
        // Port must survive: credential `git` stripped, `:22` kept on host.
        XCTAssertEqual(absString("ssh://git@host:22/u/r.git"),
                       "https://host:22/u/r")
    }

    // MARK: - git:// form

    func testGitSchemeConvertedToHTTPS() {
        XCTAssertEqual(absString("git://github.com/user/repo.git"),
                       "https://github.com/user/repo")
    }

    // MARK: - HTTPS form

    func testHTTPSStripsGitSuffix() {
        XCTAssertEqual(absString("https://host/u/r.git"),
                       "https://host/u/r")
    }

    func testHTTPSWithoutGitSuffixUnchanged() {
        XCTAssertEqual(absString("https://github.com/user/repo"),
                       "https://github.com/user/repo")
    }

    func testHTTPSWithTokenStripsCredential() {
        // `token` before @ has no dot → stripped.
        XCTAssertEqual(absString("https://token@github.com/u/r.git"),
                       "https://github.com/u/r")
    }

    // MARK: - Credential-strip heuristic

    func testCredentialWithDotIsNotStripped() {
        // ACTUAL BEHAVIOUR: the part before `@` contains a dot, so the
        // heuristic treats it as a hostname and does NOT strip it. The
        // `user.name@` prefix is preserved in the output URL.
        XCTAssertEqual(absString("https://user.name@github.com/u/r.git"),
                       "https://user.name@github.com/u/r")
    }

    func testDoubleAtCollapsesEmptyCredential() {
        // ACTUAL BEHAVIOUR: SCP parsing drops everything up to the first `@`,
        // leaving a leading `@`. stripCredentials sees an empty credential
        // (no dot) and removes it, producing a clean host URL.
        XCTAssertEqual(absString("git@@github.com:u/r.git"),
                       "https://github.com/u/r")
    }

    // MARK: - Negatives

    func testEmptyStringReturnsNil() {
        XCTAssertNil(GitURLConverter.browserURL(from: ""))
    }

    func testWhitespaceOnlyReturnsNil() {
        XCTAssertNil(GitURLConverter.browserURL(from: "   \n\t "))
    }

    func testNotAURLReturnsNil() {
        // No scheme, no `@`, contains spaces → fails final URL/scheme guard.
        XCTAssertNil(GitURLConverter.browserURL(from: "not a url"))
    }

    func testPlainHostNoSchemeReturnsNil() {
        // No scheme prefix and no SCP `@` → never gains an https:// prefix,
        // so the scheme guard rejects it.
        XCTAssertNil(GitURLConverter.browserURL(from: "github.com/user/repo"))
    }
}

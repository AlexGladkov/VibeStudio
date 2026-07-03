import XCTest
@testable import VibeStudio

/// Unit tests for ``RemoteStaticFileServer.isPathWithinBase(fileName:baseURL:)``
/// — the defense-in-depth path-traversal guard. Pure URL arithmetic, no FS
/// access.
final class StaticFilePathTests: XCTestCase {

    private let base = URL(fileURLWithPath: "/base/dir", isDirectory: true)

    func testParentTraversalEscapesBaseReturnsFalse() {
        XCTAssertFalse(
            RemoteStaticFileServer.isPathWithinBase(fileName: "../../etc/passwd", baseURL: base)
        )
    }

    func testEmbeddedTraversalEscapesBaseReturnsFalse() {
        XCTAssertFalse(
            RemoteStaticFileServer.isPathWithinBase(fileName: "vendor/../../secret", baseURL: base)
        )
    }

    func testVendorAssetIsWithinBase() {
        XCTAssertTrue(
            RemoteStaticFileServer.isPathWithinBase(fileName: "vendor/xterm.js", baseURL: base)
        )
    }

    func testTopLevelAssetIsWithinBase() {
        XCTAssertTrue(
            RemoteStaticFileServer.isPathWithinBase(fileName: "index.html", baseURL: base)
        )
    }

    /// Documents REAL behavior: `URL.appendingPathComponent` neutralizes a
    /// leading-slash "absolute" component by appending it as a sub-path of the
    /// base (`/base/dir/etc/passwd`), so it stays within base and returns
    /// `true`. The guard's actual protection is against `..` traversal, not
    /// absolute paths (which cannot escape via this API).
    func testLeadingSlashComponentStaysWithinBase() {
        XCTAssertTrue(
            RemoteStaticFileServer.isPathWithinBase(fileName: "/etc/passwd", baseURL: base)
        )
    }

    /// A sibling directory whose name merely starts with the base path must NOT
    /// be treated as within-base. `../dirAssets/x` resolves to `/base/dirAssets/x`
    /// which shares the `/base/dir` prefix textually but is a different tree.
    /// Guards against the `hasPrefix` without trailing-`/` defect.
    func testSiblingPrefixDirectoryIsNotWithinBase() {
        XCTAssertFalse(
            RemoteStaticFileServer.isPathWithinBase(fileName: "../dirAssets/evil.js", baseURL: base)
        )
    }
}

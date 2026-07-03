import XCTest
@testable import VibeStudio

/// Unit tests for ``MIMETypeResolver.mimeType(for:)`` — the single source of
/// truth for extension → Content-Type mapping. Pure static lookup.
final class MIMETypeResolverTests: XCTestCase {

    func testJavaScript() {
        XCTAssertEqual(MIMETypeResolver.mimeType(for: "js"), "application/javascript")
    }

    func testCSS() {
        XCTAssertEqual(MIMETypeResolver.mimeType(for: "css"), "text/css")
    }

    func testHTML() {
        XCTAssertEqual(MIMETypeResolver.mimeType(for: "html"), "text/html")
    }

    func testJSON() {
        XCTAssertEqual(MIMETypeResolver.mimeType(for: "json"), "application/json")
    }

    func testSVG() {
        XCTAssertEqual(MIMETypeResolver.mimeType(for: "svg"), "image/svg+xml")
    }

    func testWOFF2() {
        XCTAssertEqual(MIMETypeResolver.mimeType(for: "woff2"), "font/woff2")
    }

    func testLookupIsCaseInsensitive() {
        XCTAssertEqual(MIMETypeResolver.mimeType(for: "JS"), "application/javascript")
        XCTAssertEqual(MIMETypeResolver.mimeType(for: "Html"), "text/html")
    }

    func testUnknownExtensionFallsBackToOctetStream() {
        XCTAssertEqual(MIMETypeResolver.mimeType(for: "xyz"), "application/octet-stream")
    }

    func testEmptyExtensionFallsBackToOctetStream() {
        XCTAssertEqual(MIMETypeResolver.mimeType(for: ""), "application/octet-stream")
    }
}

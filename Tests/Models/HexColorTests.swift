import XCTest
import SwiftUI
import AppKit
@testable import VibeStudio

final class HexColorTests: XCTestCase {

    // MARK: - NSColor(hex:) Tests

    func testNSColorSixDigitRed() {
        let color = NSColor(hex: "#FF0000").usingColorSpace(.sRGB)!
        XCTAssertEqual(color.redComponent, 1.0, accuracy: 0.01)
        XCTAssertEqual(color.greenComponent, 0.0, accuracy: 0.01)
        XCTAssertEqual(color.blueComponent, 0.0, accuracy: 0.01)
        XCTAssertEqual(color.alphaComponent, 1.0, accuracy: 0.01)
    }

    func testNSColorSixDigitGreen() {
        let color = NSColor(hex: "#00FF00").usingColorSpace(.sRGB)!
        XCTAssertEqual(color.redComponent, 0.0, accuracy: 0.01)
        XCTAssertEqual(color.greenComponent, 1.0, accuracy: 0.01)
        XCTAssertEqual(color.blueComponent, 0.0, accuracy: 0.01)
    }

    func testNSColorSixDigitBlue() {
        let color = NSColor(hex: "#0000FF").usingColorSpace(.sRGB)!
        XCTAssertEqual(color.redComponent, 0.0, accuracy: 0.01)
        XCTAssertEqual(color.greenComponent, 0.0, accuracy: 0.01)
        XCTAssertEqual(color.blueComponent, 1.0, accuracy: 0.01)
    }

    func testNSColorSixDigitWhite() {
        let color = NSColor(hex: "#FFFFFF").usingColorSpace(.sRGB)!
        XCTAssertEqual(color.redComponent, 1.0, accuracy: 0.01)
        XCTAssertEqual(color.greenComponent, 1.0, accuracy: 0.01)
        XCTAssertEqual(color.blueComponent, 1.0, accuracy: 0.01)
    }

    func testNSColorSixDigitBlack() {
        let color = NSColor(hex: "#000000").usingColorSpace(.sRGB)!
        XCTAssertEqual(color.redComponent, 0.0, accuracy: 0.01)
        XCTAssertEqual(color.greenComponent, 0.0, accuracy: 0.01)
        XCTAssertEqual(color.blueComponent, 0.0, accuracy: 0.01)
    }

    func testNSColorWithoutHash() {
        let color = NSColor(hex: "FF0000").usingColorSpace(.sRGB)!
        XCTAssertEqual(color.redComponent, 1.0, accuracy: 0.01)
        XCTAssertEqual(color.greenComponent, 0.0, accuracy: 0.01)
        XCTAssertEqual(color.blueComponent, 0.0, accuracy: 0.01)
    }

    func testNSColorLowercaseHex() {
        let color = NSColor(hex: "#ff0000").usingColorSpace(.sRGB)!
        XCTAssertEqual(color.redComponent, 1.0, accuracy: 0.01)
        XCTAssertEqual(color.greenComponent, 0.0, accuracy: 0.01)
        XCTAssertEqual(color.blueComponent, 0.0, accuracy: 0.01)
    }

    func testNSColorMixedCaseHex() {
        let color = NSColor(hex: "#FfAa00").usingColorSpace(.sRGB)!
        XCTAssertEqual(color.redComponent, 1.0, accuracy: 0.01)
        XCTAssertEqual(color.greenComponent, CGFloat(0xAA) / 255.0, accuracy: 0.01)
        XCTAssertEqual(color.blueComponent, 0.0, accuracy: 0.01)
    }

    // MARK: - NSColor 8-digit (AARRGGBB)

    func testNSColorEightDigitFullAlpha() {
        let color = NSColor(hex: "#FFFF0000").usingColorSpace(.sRGB)!
        XCTAssertEqual(color.alphaComponent, 1.0, accuracy: 0.01)
        XCTAssertEqual(color.redComponent, 1.0, accuracy: 0.01)
        XCTAssertEqual(color.greenComponent, 0.0, accuracy: 0.01)
        XCTAssertEqual(color.blueComponent, 0.0, accuracy: 0.01)
    }

    func testNSColorEightDigitHalfAlpha() {
        let color = NSColor(hex: "#80FF0000").usingColorSpace(.sRGB)!
        let expectedAlpha = CGFloat(0x80) / 255.0
        XCTAssertEqual(color.alphaComponent, expectedAlpha, accuracy: 0.01)
        XCTAssertEqual(color.redComponent, 1.0, accuracy: 0.01)
        XCTAssertEqual(color.greenComponent, 0.0, accuracy: 0.01)
        XCTAssertEqual(color.blueComponent, 0.0, accuracy: 0.01)
    }

    func testNSColorEightDigitZeroAlpha() {
        let color = NSColor(hex: "#00FF0000").usingColorSpace(.sRGB)!
        XCTAssertEqual(color.alphaComponent, 0.0, accuracy: 0.01)
    }

    // MARK: - NSColor Invalid Input (fallback to black)

    func testNSColorInvalidHexFallsBackToBlack() {
        // parseHexComponents returns nil for 3-digit input,
        // so the convenience init uses fallback (black, alpha 1).
        let color = NSColor(hex: "#FFF").usingColorSpace(.sRGB)!
        XCTAssertEqual(color.redComponent, 0.0, accuracy: 0.01)
        XCTAssertEqual(color.greenComponent, 0.0, accuracy: 0.01)
        XCTAssertEqual(color.blueComponent, 0.0, accuracy: 0.01)
        XCTAssertEqual(color.alphaComponent, 1.0, accuracy: 0.01)
    }

    func testNSColorEmptyStringFallsBackToBlack() {
        let color = NSColor(hex: "").usingColorSpace(.sRGB)!
        XCTAssertEqual(color.redComponent, 0.0, accuracy: 0.01)
        XCTAssertEqual(color.greenComponent, 0.0, accuracy: 0.01)
        XCTAssertEqual(color.blueComponent, 0.0, accuracy: 0.01)
    }

    // MARK: - Color(hex:) Tests
    // SwiftUI Color components cannot be directly inspected pre-iOS 17 / macOS 14.
    // We convert to NSColor for verification.

    func testSwiftUIColorSixDigitRed() {
        let swiftUIColor = Color(hex: "#FF0000")
        let nsColor = NSColor(swiftUIColor).usingColorSpace(.sRGB)!
        XCTAssertEqual(nsColor.redComponent, 1.0, accuracy: 0.02)
        XCTAssertEqual(nsColor.greenComponent, 0.0, accuracy: 0.02)
        XCTAssertEqual(nsColor.blueComponent, 0.0, accuracy: 0.02)
    }

    func testSwiftUIColorSixDigitGreen() {
        let swiftUIColor = Color(hex: "00FF00")
        let nsColor = NSColor(swiftUIColor).usingColorSpace(.sRGB)!
        XCTAssertEqual(nsColor.redComponent, 0.0, accuracy: 0.02)
        XCTAssertEqual(nsColor.greenComponent, 1.0, accuracy: 0.02)
        XCTAssertEqual(nsColor.blueComponent, 0.0, accuracy: 0.02)
    }

    func testSwiftUIColorLowercase() {
        let swiftUIColor = Color(hex: "#ff0000")
        let nsColor = NSColor(swiftUIColor).usingColorSpace(.sRGB)!
        XCTAssertEqual(nsColor.redComponent, 1.0, accuracy: 0.02)
        XCTAssertEqual(nsColor.greenComponent, 0.0, accuracy: 0.02)
        XCTAssertEqual(nsColor.blueComponent, 0.0, accuracy: 0.02)
    }

    func testSwiftUIColorEightDigitAlpha() {
        let swiftUIColor = Color(hex: "#80FF0000")
        let nsColor = NSColor(swiftUIColor).usingColorSpace(.sRGB)!
        let expectedAlpha = CGFloat(0x80) / 255.0
        XCTAssertEqual(nsColor.alphaComponent, expectedAlpha, accuracy: 0.02)
        XCTAssertEqual(nsColor.redComponent, 1.0, accuracy: 0.02)
    }

    // MARK: - Design System Token Spot Checks

    func testDesignTokenSurfaceBaseIsDark() {
        // DSColor.surfaceBase = "#1A1B1E" -- dark background
        let nsColor = NSColor(hex: "#1A1B1E").usingColorSpace(.sRGB)!
        XCTAssertLessThan(nsColor.redComponent, 0.15)
        XCTAssertLessThan(nsColor.greenComponent, 0.15)
        XCTAssertLessThan(nsColor.blueComponent, 0.15)
    }

    func testDesignTokenTextPrimaryIsLight() {
        // DSColor.textPrimary = "#D4D4D8" -- light text
        let nsColor = NSColor(hex: "#D4D4D8").usingColorSpace(.sRGB)!
        XCTAssertGreaterThan(nsColor.redComponent, 0.8)
        XCTAssertGreaterThan(nsColor.greenComponent, 0.8)
        XCTAssertGreaterThan(nsColor.blueComponent, 0.8)
    }

    // MARK: - HexColor Model Tests

    func testHexColorValidSixDigit() {
        let hex = HexColor("#FF0000")
        XCTAssertNotNil(hex)
        XCTAssertEqual(hex?.value, "#FF0000")
    }

    func testHexColorValidWithoutHash() {
        let hex = HexColor("00FF00")
        XCTAssertNotNil(hex)
        XCTAssertEqual(hex?.value, "#00FF00")
    }

    func testHexColorInvalidTooShort() {
        let hex = HexColor("FFF")
        XCTAssertNil(hex)
    }

    func testHexColorInvalidTooLong() {
        let hex = HexColor("FF00FF00FF")
        XCTAssertNil(hex)
    }

    func testHexColorInvalidCharacters() {
        let hex = HexColor("ZZZZZZ")
        XCTAssertNil(hex)
    }

    func testHexColorEmptyString() {
        let hex = HexColor("")
        XCTAssertNil(hex)
    }

    func testHexColorLowercaseNormalized() {
        let hex = HexColor("abcdef")
        XCTAssertNotNil(hex)
        XCTAssertEqual(hex?.value, "#abcdef")
    }

    // MARK: - HexColor Codable

    func testHexColorRoundTripCodable() throws {
        let original = HexColor("#AABBCC")!
        let data = try JSONEncoder().encode(original)
        let decoded = try JSONDecoder().decode(HexColor.self, from: data)
        XCTAssertEqual(original, decoded)
    }

    func testHexColorDecodingInvalidThrows() {
        let json = "\"not-a-color\""
        let data = json.data(using: .utf8)!
        XCTAssertThrowsError(try JSONDecoder().decode(HexColor.self, from: data))
    }
}

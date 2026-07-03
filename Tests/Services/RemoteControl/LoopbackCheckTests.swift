import XCTest
@testable import VibeStudio

/// Unit tests for ``LoopbackCheck.isLoopback`` — the single source of truth for
/// loopback classification used by the WS-upgrade SEC-H2 gate and the debug
/// endpoints SEC-1 gate. Pure function: no channel, network, or FS state.
final class LoopbackCheckTests: XCTestCase {

    // MARK: - Loopback (true)

    func testIPv4LoopbackCanonical() {
        XCTAssertTrue(LoopbackCheck.isLoopback("127.0.0.1"))
    }

    func testIPv4LoopbackAnywhereInBlock() {
        // Entire 127.0.0.0/8 block is loopback.
        XCTAssertTrue(LoopbackCheck.isLoopback("127.5.5.5"))
    }

    func testIPv4LoopbackHighestHostInBlock() {
        // Top of the 127.0.0.0/8 block is still loopback.
        XCTAssertTrue(LoopbackCheck.isLoopback("127.255.255.254"))
    }

    func testIPv6LoopbackCompact() {
        XCTAssertTrue(LoopbackCheck.isLoopback("::1"))
    }

    func testIPv6LoopbackExpanded() {
        XCTAssertTrue(LoopbackCheck.isLoopback("0:0:0:0:0:0:0:1"))
    }

    func testIPv4MappedIPv6Loopback() {
        XCTAssertTrue(LoopbackCheck.isLoopback("::ffff:127.0.0.1"))
    }

    // MARK: - Non-loopback (false)

    func testAdjacentToLoopbackBlockIsNotLoopback() {
        XCTAssertFalse(LoopbackCheck.isLoopback("128.0.0.1"))
    }

    func testJustBelowLoopbackBlockIsNotLoopback() {
        XCTAssertFalse(LoopbackCheck.isLoopback("126.255.255.255"))
    }

    func testPrivateLANAddressIsNotLoopback() {
        XCTAssertFalse(LoopbackCheck.isLoopback("10.0.0.1"))
    }

    func testIPv4MappedIPv6NonLoopback() {
        XCTAssertFalse(LoopbackCheck.isLoopback("::ffff:128.0.0.1"))
    }

    func testDecimalEncodedLoopbackIsNotAccepted() {
        // 2130706433 == 127.0.0.1 decimal, but only dotted-quad is trusted.
        XCTAssertFalse(LoopbackCheck.isLoopback("2130706433"))
    }

    func testHostnameSpoofPrefixIsNotLoopback() {
        // "127.evil.com" starts with "127." textually but is NOT a valid IPv4
        // loopback address. The hardened check requires a real dotted-quad, so
        // a spoofed-hostname prefix is rejected (defense-in-depth).
        XCTAssertFalse(LoopbackCheck.isLoopback("127.evil.com"))
    }

    func testOctetOutOfRangeIsNotLoopback() {
        // 256 is not a valid octet — reject even with the 127. prefix.
        XCTAssertFalse(LoopbackCheck.isLoopback("127.0.0.256"))
    }

    func testTooFewOctetsIsNotLoopback() {
        XCTAssertFalse(LoopbackCheck.isLoopback("127.0.1"))
    }

    func testTrailingGarbageOctetIsNotLoopback() {
        XCTAssertFalse(LoopbackCheck.isLoopback("127.0.0.1x"))
    }

    func testEmptyStringIsNotLoopback() {
        XCTAssertFalse(LoopbackCheck.isLoopback(""))
    }
}

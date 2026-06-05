// MARK: - TLSCertificateManagerTests
// Tests certificate generation, on-disk persistence, fingerprint computation.
//
// NOTE: TLSCertificateManager writes to ~/Library/Application Support/VibeStudio/remote-tls
// (path is private/static). Tests are non-destructive: they assert ensureCertificate
// produces a valid certificate the second call reuses, and fingerprint format is hex.

import XCTest
@testable import VibeStudio

final class TLSCertificateManagerTests: XCTestCase {

    func test_ensureCertificate_returnsPathsThatExistOnDisk() throws {
        let (certPath, keyPath) = try TLSCertificateManager.ensureCertificate()
        XCTAssertTrue(FileManager.default.fileExists(atPath: certPath.path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: keyPath.path))
    }

    func test_ensureCertificate_generatesPEMFormat() throws {
        let (certPath, keyPath) = try TLSCertificateManager.ensureCertificate()
        let cert = try String(contentsOf: certPath, encoding: .utf8)
        let key = try String(contentsOf: keyPath, encoding: .utf8)
        XCTAssertTrue(cert.contains("-----BEGIN CERTIFICATE-----"))
        XCTAssertTrue(cert.contains("-----END CERTIFICATE-----"))
        XCTAssertTrue(key.contains("-----BEGIN"))
    }

    func test_ensureCertificate_keyFileIsOwnerReadable_chmod600() throws {
        let (_, keyPath) = try TLSCertificateManager.ensureCertificate()
        let attrs = try FileManager.default.attributesOfItem(atPath: keyPath.path)
        guard let perms = attrs[.posixPermissions] as? NSNumber else {
            return XCTFail("posixPermissions missing")
        }
        // SECURITY invariant: private key must not be world/group-readable.
        XCTAssertEqual(perms.intValue & 0o077, 0, "private key has overly-permissive bits: \(String(perms.intValue, radix: 8))")
    }

    func test_ensureCertificate_calledTwice_returnsSamePaths() throws {
        let first = try TLSCertificateManager.ensureCertificate()
        let second = try TLSCertificateManager.ensureCertificate()
        XCTAssertEqual(first.certPath.path, second.certPath.path)
        XCTAssertEqual(first.keyPath.path, second.keyPath.path)
    }

    func test_certificateFingerprint_isHexColonSeparated() throws {
        _ = try TLSCertificateManager.ensureCertificate()
        guard let fp = TLSCertificateManager.certificateFingerprint() else {
            return XCTFail("no fingerprint after ensureCertificate")
        }
        // SHA-256 = 32 bytes -> 32 hex pairs separated by 31 colons.
        let parts = fp.components(separatedBy: ":")
        XCTAssertEqual(parts.count, 32)
        XCTAssertTrue(parts.allSatisfy { $0.count == 2 && $0.allSatisfy { c in c.isHexDigit } })
    }
}

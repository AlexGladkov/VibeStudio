// MARK: - TLSCertificateManager
// Self-signed TLS certificate generation and management for Remote Control.
// macOS 14+, Swift 5.10
//
// NOTE: This file depends on swift-certificates (X509) and swift-crypto (Crypto)
// SPM packages. It will not compile until those dependencies are added to
// Package.swift / Xcode project in Wave 2.

import Foundation
import OSLog
import X509
import Crypto
import SwiftASN1

/// Manages self-signed TLS certificates for the Remote Control HTTPS server.
///
/// Certificates are stored in `~/Library/Application Support/VibeStudio/remote-tls/`.
/// A new certificate is generated when:
/// - No certificate exists on disk.
/// - The existing certificate is corrupted or unreadable.
/// - The existing certificate has expired.
///
/// **Security:**
/// - Private key file is chmod 600 (owner read/write only).
/// - Certificate uses ECDSA P-256 with SHA-256 signing.
/// - Subject Alternative Names: `localhost`, `127.0.0.1`.
/// - Validity: 1 year from generation.
/// Errors thrown by ``TLSCertificateManager``.
enum CertificateError: LocalizedError {
    case invalidValidity
    case encodingFailed(String)
    /// The Application Support directory cannot be located on this system.
    /// Replaces the previous `preconditionFailure` (M14) so the calling layer
    /// can surface the failure to the user instead of crashing the process.
    case storageUnavailable

    var errorDescription: String? {
        switch self {
        case .invalidValidity:
            return "Failed to compute certificate validity period"
        case .encodingFailed(let component):
            return "Failed to encode \(component) to UTF-8"
        case .storageUnavailable:
            return "Application Support directory is unavailable; cannot persist TLS certificate"
        }
    }
}

struct TLSCertificateManager {

    // MARK: - Constants

    private static let certFilename = "server.pem"
    private static let keyFilename = "server-key.pem"
    private static let validityDays = 365

    /// Storage directory for TLS certificate and private key.
    ///
    /// Resolved lazily on each call rather than as a `static let` so that a
    /// missing Application Support directory throws ``CertificateError/storageUnavailable``
    /// instead of crashing the process via `preconditionFailure` (M14).
    private static func storageDir() throws -> URL {
        guard let appSupport = FileManager.default.urls(
            for: .applicationSupportDirectory,
            in: .userDomainMask
        ).first else {
            throw CertificateError.storageUnavailable
        }
        return appSupport.appendingPathComponent("VibeStudio/remote-tls", isDirectory: true)
    }

    // MARK: - Public API

    /// Ensure a valid TLS certificate exists, generating one if necessary.
    ///
    /// - Returns: Paths to the certificate PEM file and private key PEM file.
    /// - Throws: If certificate generation or disk I/O fails.
    static func ensureCertificate() throws -> (certPath: URL, keyPath: URL) {
        // Try to load existing certificate first.
        if let existing = try? loadFromDisk() {
            // Verify the certificate is not expired.
            if let certData = try? Data(contentsOf: existing.certPath),
               let certPEM = String(data: certData, encoding: .utf8),
               isCertificateValid(pem: certPEM) {
                Logger.remoteControl.info("TLS certificate loaded from disk")
                return existing
            }
            Logger.remoteControl.info("Existing TLS certificate expired or invalid, regenerating")
        }

        // Generate a new self-signed certificate.
        let (cert, key) = try generateSelfSigned()
        let paths = try saveToDisk(cert: cert, key: key)
        Logger.remoteControl.info("New self-signed TLS certificate generated")
        return paths
    }

    /// Compute the SHA-256 fingerprint of the current certificate.
    ///
    /// Returns the hex-encoded fingerprint string (e.g. `"AB:CD:EF:..."`)
    /// or `nil` if no certificate exists.
    static func certificateFingerprint() -> String? {
        guard let dir = try? storageDir() else { return nil }
        let certPath = dir.appendingPathComponent(certFilename)
        guard let certData = try? Data(contentsOf: certPath),
              let pem = String(data: certData, encoding: .utf8),
              let pemDoc = try? PEMDocument(pemString: pem) else { return nil }

        let hash = SHA256.hash(data: Data(pemDoc.derBytes))
        return hash.map { String(format: "%02X", $0) }.joined(separator: ":")
    }

    // MARK: - Private: Certificate Generation

    /// Generate a self-signed ECDSA P-256 certificate.
    private static func generateSelfSigned() throws -> (cert: Certificate, key: P256.Signing.PrivateKey) {
        let privateKey = P256.Signing.PrivateKey()

        let subject = try DistinguishedName {
            CommonName("VibeStudio Remote Control")
            OrganizationName("VibeStudio")
        }

        let now = Date()
        let notBefore = now
        guard let notAfter = Calendar.current.date(byAdding: .day, value: validityDays, to: now) else {
            throw CertificateError.invalidValidity
        }

        // Subject Alternative Names: localhost + loopback IP.
        let extensions = try Certificate.Extensions {
            SubjectAlternativeNames([
                .dnsName("localhost"),
                .ipAddress(ASN1OctetString(contentBytes: [127, 0, 0, 1][...]))
            ])

            // Mark as CA: false (end-entity certificate).
            BasicConstraints.notCertificateAuthority

            // Key usage: digital signature (for TLS handshake).
            KeyUsage(digitalSignature: true)

            // Extended key usage: TLS server authentication.
            try ExtendedKeyUsage([.serverAuth])
        }

        let cert = try Certificate(
            version: .v3,
            serialNumber: Certificate.SerialNumber(),
            publicKey: .init(privateKey.publicKey),
            notValidBefore: notBefore,
            notValidAfter: notAfter,
            issuer: subject,
            subject: subject,
            signatureAlgorithm: .ecdsaWithSHA256,
            extensions: extensions,
            issuerPrivateKey: .init(privateKey)
        )

        return (cert, privateKey)
    }

    // MARK: - Private: Persistence

    /// Save certificate and private key to disk in PEM format.
    private static func saveToDisk(
        cert: Certificate,
        key: P256.Signing.PrivateKey
    ) throws -> (certPath: URL, keyPath: URL) {
        let fm = FileManager.default
        let dir = try storageDir()

        // Create storage directory if needed.
        if !fm.fileExists(atPath: dir.path) {
            try fm.createDirectory(at: dir, withIntermediateDirectories: true)
        }

        let certPath = dir.appendingPathComponent(certFilename)
        let keyPath = dir.appendingPathComponent(keyFilename)

        // Serialize certificate to PEM.
        var certSerializer = DER.Serializer()
        try cert.serialize(into: &certSerializer)
        let certPEM = PEMDocument(type: "CERTIFICATE", derBytes: certSerializer.serializedBytes)
        guard let certData = certPEM.pemString.data(using: .utf8) else {
            throw CertificateError.encodingFailed("certificate PEM")
        }
        try certData.write(to: certPath)

        // Serialize private key to PEM.
        let keyDER = key.derRepresentation
        let keyPEM = PEMDocument(type: "EC PRIVATE KEY", derBytes: Array(keyDER))
        guard let keyData = keyPEM.pemString.data(using: .utf8) else {
            throw CertificateError.encodingFailed("private key PEM")
        }
        try keyData.write(to: keyPath)

        // SECURITY: Restrict private key file permissions to owner-only.
        try fm.setAttributes(
            [.posixPermissions: 0o600],
            ofItemAtPath: keyPath.path
        )

        return (certPath, keyPath)
    }

    /// Load existing certificate and key paths from disk.
    ///
    /// Returns `nil` if either file is missing.
    private static func loadFromDisk() throws -> (certPath: URL, keyPath: URL)? {
        let fm = FileManager.default
        let dir = try storageDir()
        let certPath = dir.appendingPathComponent(certFilename)
        let keyPath = dir.appendingPathComponent(keyFilename)

        guard fm.fileExists(atPath: certPath.path),
              fm.fileExists(atPath: keyPath.path) else {
            return nil
        }

        return (certPath, keyPath)
    }

    // MARK: - Private: Validation

    /// Check whether a PEM-encoded certificate is still within its validity period.
    private static func isCertificateValid(pem: String) -> Bool {
        do {
            let pemDoc = try PEMDocument(pemString: pem)
            let cert = try Certificate(derEncoded: pemDoc.derBytes)
            let now = Date()
            return now >= cert.notValidBefore && now <= cert.notValidAfter
        } catch {
            Logger.remoteControl.warning("Failed to parse certificate for validity check: \(error.localizedDescription)")
            return false
        }
    }
}

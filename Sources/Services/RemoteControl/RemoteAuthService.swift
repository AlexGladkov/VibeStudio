// MARK: - RemoteAuthService
// PIN-based authentication and token management for Remote Control.
// macOS 14+, Swift 5.10

import Foundation
import Observation
import OSLog
import Security

// MARK: - Supporting Types

/// A remote device that has successfully authenticated via PIN.
struct RemoteDevice: Identifiable, Codable {
    let id: UUID
    let displayName: String
    let ipAddress: String
    let connectedAt: Date
}

/// Internal token storage entry -- not exposed to clients.
struct TokenEntry {
    let deviceId: UUID
    let clientIP: String
    let issuedAt: Date
    let expiresAt: Date
}

/// Successful authentication response.
struct AuthTokenResponse {
    let token: String
    let device: RemoteDevice
}

/// Authentication errors with machine-readable context.
enum AuthError: Error, Equatable {
    case invalidPin
    case rateLimited(retryAfterSeconds: Int)
    case globalLockout
    case invalidToken
    case tokenExpired
    case ipMismatch
    case maxDevicesReached
}

// MARK: - RemoteAuthService

/// Manages PIN generation, token issuance, rate limiting, and device tracking
/// for the Remote Control server.
///
/// **Security invariants:**
/// - PIN is 6 cryptographic random digits (`SecRandomCopyBytes`).
/// - PIN is one-time-use: consumed on successful validation, then regenerated.
/// - Token is 32 random bytes hex-encoded, IP-bound, 4-hour TTL.
/// - Per-IP rate limit: 3 failures within 5 minutes = IP lockout.
/// - Global rate limit: 10 total failures = `isLocked` = server auto-disables.
/// - All authentication events are logged via `os_log`.
@Observable
@MainActor
final class RemoteAuthService {

    // MARK: - Public Constants

    /// Maximum number of simultaneously connected remote devices.
    /// Exposed so other components (HTTPRequestRouter, UI) can reference
    /// the single source of truth rather than duplicating the magic number.
    static let maxDevices = 3

    /// Token validity duration, exposed for HTTP layer use (`expiresAt` claim,
    /// `Retry-After` hints). Mirrors ``Constants/tokenTTL``.
    ///
    /// `nonisolated` because the constant is also read from NIO event-loop
    /// threads (router responses) and must not require a MainActor hop.
    nonisolated static let tokenTTL: TimeInterval = 4 * 60 * 60

    /// Public mirror of ``Constants/rateLimitWindowSeconds`` so the HTTP layer
    /// can emit reasonable `Retry-After` hints without duplicating the literal.
    ///
    /// `nonisolated` for the same reason as ``tokenTTL`` — consumed from NIO
    /// handler threads.
    nonisolated static let rateLimitWindowSecondsPublic: TimeInterval = 5 * 60

    // MARK: - Private Constants

    private enum Constants {
        /// Number of random bytes for the PIN source.
        static let pinByteCount = 4
        /// Number of random bytes for token generation.
        static let tokenByteCount = 32
        /// Token validity duration.
        static let tokenTTL: TimeInterval = 4 * 60 * 60 // 4 hours
        /// Maximum failed attempts per IP within the rate-limit window.
        static let maxFailuresPerIP = 3
        /// Rate-limit window duration in seconds.
        static let rateLimitWindowSeconds: TimeInterval = 5 * 60 // 5 minutes
        /// Global failure threshold -- after this many total failures, the
        /// server locks out completely until manual reset.
        static let globalLockoutThreshold = 10
    }

    // MARK: - Observable State

    /// The current 6-digit PIN displayed to the user.
    private(set) var currentPin: String = ""

    /// Currently authenticated and connected remote devices.
    private(set) var connectedDevices: [RemoteDevice] = []

    /// When `true`, the server is globally locked out due to excessive
    /// failed authentication attempts. Must be reset manually.
    private(set) var isLocked: Bool = false

    /// Called when the server should shut down due to excessive failed auth attempts.
    var onSecurityLockout: (() -> Void)?

    /// Called when the connected devices list changes (add/remove).
    var onDevicesChanged: ((_ count: Int) -> Void)?

    // MARK: - Private State

    /// Active tokens: token string -> entry.
    private var tokens: [String: TokenEntry] = [:]

    /// Per-IP failed attempt timestamps for sliding-window rate limiting.
    private var failedAttempts: [String: [Date]] = [:]

    /// Total failed attempts across all IPs (never reset automatically).
    private var globalFailedCount: Int = 0

    // MARK: - Init

    init() {
        regeneratePin()
    }

    // MARK: - PIN Management

    /// Generate a new cryptographically random 6-digit PIN.
    ///
    /// Called at init, after a successful authentication, and after a rate-limit
    /// lockout event.
    func regeneratePin() {
        // SECURITY (M2): use rejection sampling to obtain an unbiased 6-digit
        // PIN. UInt32.max + 1 (== 2^32) does not divide cleanly by 1_000_000,
        // so a plain `raw % 1_000_000` favours the low 296-PIN range with a
        // ~0.023% bias. We reject samples in the tail that would skew the
        // distribution and resample until the value falls within the
        // uniformly representable range.
        //
        // Largest multiple of 1_000_000 strictly ≤ UInt32.max:
        //   floor(2^32 / 1_000_000) * 1_000_000 == 4_294_000_000.
        // Sampling `raw` from [0, 4_294_000_000) then taking `raw % 1_000_000`
        // yields a uniform distribution across [0, 1_000_000).
        let acceptanceCeiling: UInt32 = 4_294_000_000
        var bytes = [UInt8](repeating: 0, count: Constants.pinByteCount)
        var raw: UInt32 = 0
        repeat {
            let status = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
            guard status == errSecSuccess else {
                // SecRandomCopyBytes failing indicates a catastrophic system error.
                // Refusing to generate a weak PIN -- crash is safer than silent downgrade.
                fatalError("SecRandomCopyBytes failed with status \(status) -- cannot generate secure PIN")
            }
            raw = bytes.withUnsafeBytes { $0.load(as: UInt32.self) }
        } while raw >= acceptanceCeiling

        let pin = raw % 1_000_000
        currentPin = String(format: "%06d", pin)
        Logger.remoteControl.info("PIN regenerated")
    }

    // MARK: - PIN Validation

    /// Validate a PIN submitted by a remote client.
    ///
    /// On success, the PIN is consumed (regenerated) and a token is issued.
    /// On failure, rate-limiting counters are incremented.
    ///
    /// - Parameters:
    ///   - pin: The 6-digit PIN string from the client.
    ///   - clientIP: The client's IP address for rate limiting and token binding.
    ///   - userAgent: The client's User-Agent header for device display name.
    /// - Returns: An `AuthTokenResponse` on success, or an `AuthError` on failure.
    func validatePin(
        _ pin: String,
        clientIP: String,
        userAgent: String
    ) -> Result<AuthTokenResponse, AuthError> {
        // Check global lockout first.
        if isLocked {
            Logger.remoteControl.error("PIN validation rejected: global lockout active (IP: \(clientIP, privacy: .public))")
            return .failure(.globalLockout)
        }

        // Check max connected devices.
        if connectedDevices.count >= RemoteAuthService.maxDevices {
            Logger.remoteControl.warning("PIN validation rejected: max devices (\(RemoteAuthService.maxDevices)) reached (IP: \(clientIP, privacy: .public))")
            return .failure(.maxDevicesReached)
        }

        // Check per-IP rate limit.
        pruneExpiredAttempts(for: clientIP)
        let recentFailures = failedAttempts[clientIP] ?? []
        if recentFailures.count >= Constants.maxFailuresPerIP {
            let oldestInWindow = recentFailures.first ?? Date()
            let retryAfter = Int(Constants.rateLimitWindowSeconds - Date().timeIntervalSince(oldestInWindow))
            Logger.remoteControl.warning("PIN validation rate-limited for IP: \(clientIP, privacy: .public), retry after \(max(retryAfter, 1))s")
            return .failure(.rateLimited(retryAfterSeconds: max(retryAfter, 1)))
        }

        // Constant-time comparison to mitigate timing attacks.
        guard constantTimeEqual(pin, currentPin) else {
            recordFailure(for: clientIP)
            let remaining = Constants.maxFailuresPerIP - (failedAttempts[clientIP]?.count ?? 0)
            Logger.remoteControl.warning("Invalid PIN attempt from IP: \(clientIP, privacy: .public), \(remaining) attempts remaining")
            return .failure(.invalidPin)
        }

        // Success -- consume PIN and issue token.
        let token = generateToken()
        let deviceId = UUID()
        let now = Date()
        let expiresAt = now.addingTimeInterval(Constants.tokenTTL)
        let displayName = parseDeviceName(from: userAgent)

        let device = RemoteDevice(
            id: deviceId,
            displayName: displayName,
            ipAddress: clientIP,
            connectedAt: now
        )

        let entry = TokenEntry(
            deviceId: deviceId,
            clientIP: clientIP,
            issuedAt: now,
            expiresAt: expiresAt
        )

        tokens[token] = entry
        connectedDevices.append(device)
        onDevicesChanged?(connectedDevices.count)

        // Clear failed attempts for this IP on success.
        failedAttempts.removeValue(forKey: clientIP)

        // Regenerate PIN (one-time use).
        regeneratePin()

        let tokenCount = self.tokens.count
        Logger.remoteControl.info("Device authenticated: \(displayName, privacy: .public) from \(clientIP, privacy: .public) deviceId=\(deviceId, privacy: .public) totalTokens=\(tokenCount, privacy: .public)")

        return .success(AuthTokenResponse(token: token, device: device))
    }

    // MARK: - Token Validation

    /// Validate a bearer token from a subsequent API request.
    ///
    /// - Parameters:
    ///   - token: The opaque token string.
    ///   - clientIP: The requesting client's IP address.
    /// - Returns: The associated `RemoteDevice` on success, or an `AuthError`.
    func validateToken(_ token: String, clientIP: String) -> Result<RemoteDevice, AuthError> {
        guard let entry = tokens[token] else {
            return .failure(.invalidToken)
        }

        guard Date() < entry.expiresAt else {
            // Token expired -- remove it.
            tokens.removeValue(forKey: token)
            removeDevice(entry.deviceId)
            return .failure(.tokenExpired)
        }

        guard entry.clientIP == clientIP else {
            Logger.remoteControl.warning("Token IP mismatch: expected \(entry.clientIP, privacy: .public), got \(clientIP, privacy: .public)")
            return .failure(.ipMismatch)
        }

        guard let device = connectedDevices.first(where: { $0.id == entry.deviceId }) else {
            return .failure(.invalidToken)
        }

        return .success(device)
    }

    // MARK: - Device Management

    /// Revoke a specific device's access and remove its token.
    func revokeDevice(_ deviceId: UUID) {
        // Remove all tokens for this device.
        tokens = tokens.filter { $0.value.deviceId != deviceId }
        removeDevice(deviceId)
        Logger.remoteControl.info("Device revoked: \(deviceId)")
    }

    /// Revoke all connected devices and clear all tokens.
    func revokeAllDevices() {
        tokens.removeAll()
        connectedDevices.removeAll()
        onDevicesChanged?(0)
        Logger.remoteControl.info("All devices revoked")
    }

    /// Reset the global lockout state. Called manually by the user from Settings.
    func resetLockout() {
        isLocked = false
        globalFailedCount = 0
        failedAttempts.removeAll()
        regeneratePin()
        Logger.remoteControl.info("Global lockout reset")
    }

    // MARK: - Private: Token Generation

    /// Generate a cryptographically random 32-byte hex-encoded token.
    private func generateToken() -> String {
        var bytes = [UInt8](repeating: 0, count: Constants.tokenByteCount)
        let status = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        guard status == errSecSuccess else {
            // SecRandomCopyBytes failing indicates a catastrophic system error.
            // Refusing to generate a weak token -- crash is safer than silent downgrade.
            fatalError("SecRandomCopyBytes failed with status \(status) -- cannot generate secure token")
        }
        return bytes.map { String(format: "%02x", $0) }.joined()
    }

    // MARK: - Private: Rate Limiting

    /// Record a failed authentication attempt for rate-limiting purposes.
    private func recordFailure(for clientIP: String) {
        failedAttempts[clientIP, default: []].append(Date())
        globalFailedCount += 1

        if globalFailedCount >= Constants.globalLockoutThreshold {
            isLocked = true
            Logger.remoteControl.error("Global lockout activated after \(self.globalFailedCount) total failures")
            onSecurityLockout?()
        }
    }

    /// Remove expired entries from the per-IP failure tracking.
    ///
    /// Also cleans up empty entries to prevent unbounded dictionary growth from
    /// unique IP addresses, and performs a full sweep when the dictionary exceeds
    /// 1000 entries.
    private func pruneExpiredAttempts(for clientIP: String) {
        let cutoff = Date().addingTimeInterval(-Constants.rateLimitWindowSeconds)
        failedAttempts[clientIP] = failedAttempts[clientIP]?.filter { $0 > cutoff }
        // Remove empty entries to prevent unbounded dictionary growth from unique IPs.
        if failedAttempts[clientIP]?.isEmpty == true {
            failedAttempts.removeValue(forKey: clientIP)
        }
        // Periodic full cleanup: if dictionary grows beyond 1000 entries,
        // prune all expired entries across all IPs.
        if failedAttempts.count > 1000 {
            for ip in failedAttempts.keys {
                failedAttempts[ip] = failedAttempts[ip]?.filter { $0 > cutoff }
                if failedAttempts[ip]?.isEmpty == true {
                    failedAttempts.removeValue(forKey: ip)
                }
            }
        }
    }

    // MARK: - Private: Device Tracking

    /// Remove a device from the connected devices list.
    private func removeDevice(_ deviceId: UUID) {
        connectedDevices.removeAll { $0.id == deviceId }
        onDevicesChanged?(connectedDevices.count)
    }

    // MARK: - Private: Helpers

    /// Constant-time string comparison to mitigate timing side-channel attacks.
    ///
    /// Both inputs are padded to equal length before comparison, preventing
    /// length-based information leakage. PIN is always 6 digits, but this
    /// function handles arbitrary inputs safely.
    private func constantTimeEqual(_ a: String, _ b: String) -> Bool {
        let aBytes = [UInt8](a.utf8)
        let bBytes = [UInt8](b.utf8)
        let maxLen = max(aBytes.count, bBytes.count)
        guard maxLen > 0 else { return true }
        // XOR length mismatch into result (non-zero if lengths differ).
        var result: UInt8 = UInt8(truncatingIfNeeded: aBytes.count ^ bBytes.count)
        for i in 0..<maxLen {
            let aByte = i < aBytes.count ? aBytes[i] : 0
            let bByte = i < bBytes.count ? bBytes[i] : 0
            result |= aByte ^ bByte
        }
        return result == 0
    }

    /// Parse a human-readable device name from a User-Agent string.
    ///
    /// Examples:
    /// - `"Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X)"` -> `"iPhone"`
    /// - `"Mozilla/5.0 (iPad; ...)"` -> `"iPad"`
    /// - `"Mozilla/5.0 (Macintosh; ...)"` -> `"Mac"`
    /// - Unknown -> `"Remote Device"`
    private func parseDeviceName(from userAgent: String) -> String {
        if userAgent.contains("iPhone") { return "iPhone" }
        if userAgent.contains("iPad") { return "iPad" }
        if userAgent.contains("Macintosh") { return "Mac" }
        if userAgent.contains("Android") { return "Android" }
        if userAgent.contains("Linux") { return "Linux" }
        if userAgent.contains("Windows") { return "Windows" }
        return "Remote Device"
    }
}

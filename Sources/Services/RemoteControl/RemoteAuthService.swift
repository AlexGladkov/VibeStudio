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

    /// Single source of truth for the token validity duration (4 hours).
    /// Both the public ``tokenTTL`` mirror and the private ``Constants/tokenTTL``
    /// derive from this so the value is never duplicated.
    nonisolated private static let _tokenTTLValue: TimeInterval = 4 * 60 * 60

    /// Single source of truth for the rate-limit window (5 minutes).
    /// Both the public ``rateLimitWindowSecondsPublic`` mirror and the private
    /// ``Constants/rateLimitWindowSeconds`` derive from this.
    nonisolated private static let _rateLimitWindowValue: TimeInterval = 5 * 60

    /// Token validity duration, exposed for HTTP layer use (`expiresAt` claim,
    /// `Retry-After` hints). Mirrors ``Constants/tokenTTL``.
    ///
    /// `nonisolated` because the constant is also read from NIO event-loop
    /// threads (router responses) and must not require a MainActor hop.
    nonisolated static let tokenTTL: TimeInterval = _tokenTTLValue

    /// Public mirror of ``Constants/rateLimitWindowSeconds`` so the HTTP layer
    /// can emit reasonable `Retry-After` hints without duplicating the literal.
    ///
    /// `nonisolated` for the same reason as ``tokenTTL`` — consumed from NIO
    /// handler threads.
    nonisolated static let rateLimitWindowSecondsPublic: TimeInterval = _rateLimitWindowValue

    // MARK: - Private Constants

    private enum Constants {
        /// Number of random bytes for the PIN source.
        static let pinByteCount = 4
        /// Number of random bytes for token generation.
        static let tokenByteCount = 32
        /// Token validity duration (4 hours). Derived from the single source
        /// of truth ``RemoteAuthService/_tokenTTLValue``.
        static let tokenTTL: TimeInterval = RemoteAuthService._tokenTTLValue
        /// Maximum failed attempts per IP within the rate-limit window.
        static let maxFailuresPerIP = 3
        /// Rate-limit window duration in seconds (5 minutes). Derived from the
        /// single source of truth ``RemoteAuthService/_rateLimitWindowValue``.
        static let rateLimitWindowSeconds: TimeInterval = RemoteAuthService._rateLimitWindowValue
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

    /// Called when a token expires during validation so the server can perform
    /// the canonical full teardown (bridge + auth) for the device.
    ///
    /// P0-2 fix: `validateToken` previously called only `removeDevice`, leaving
    /// `activeBridges` with a stale entry (and the WS channel open). The server
    /// wires this callback to `disconnect(_:)` which atomically removes BOTH
    /// registries and closes the channel — matching the pattern in
    /// `RemoteControlServer.disconnect(_:)`.
    var onTokenExpired: ((_ deviceId: UUID) -> Void)?

    // MARK: - Private State

    /// Active tokens: token string -> entry.
    private var tokens: [String: TokenEntry] = [:]

    /// Per-IP failed attempt timestamps for sliding-window rate limiting.
    private var failedAttempts: [String: [Date]] = [:]

    /// Total failed attempts across all IPs (never reset automatically).
    private var globalFailedCount: Int = 0

    /// Injectable clock. Production uses the system `Date()`; tests inject a
    /// controllable source to make rate-limit windows and token TTLs
    /// deterministic. Never observed — it is a constant dependency.
    @ObservationIgnored private let now: () -> Date

    // MARK: - Init

    /// - Parameter now: Clock source for all time-based decisions (rate-limit
    ///   window, token issuance/expiry). Defaults to the system clock.
    init(now: @escaping () -> Date = { Date() }) {
        self.now = now
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
            Logger.remoteControl.warning(
                "PIN validation rejected: max devices (\(RemoteAuthService.maxDevices)) reached (IP: \(clientIP, privacy: .public))"
            )
            return .failure(.maxDevicesReached)
        }

        // Check per-IP rate limit (shared with token validation — see ``checkRateLimit``).
        if case .limited(let retryAfter) = checkRateLimit(for: clientIP) {
            Logger.remoteControl.warning("PIN validation rate-limited for IP: \(clientIP, privacy: .public), retry after \(retryAfter)s")
            return .failure(.rateLimited(retryAfterSeconds: retryAfter))
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
        let issuedAt = now()
        let expiresAt = issuedAt.addingTimeInterval(Constants.tokenTTL)
        let displayName = parseDeviceName(from: userAgent)

        let device = RemoteDevice(
            id: deviceId,
            displayName: displayName,
            ipAddress: clientIP,
            connectedAt: issuedAt
        )

        let entry = TokenEntry(
            deviceId: deviceId,
            clientIP: clientIP,
            issuedAt: issuedAt,
            expiresAt: expiresAt
        )

        tokens[token] = entry
        connectedDevices.append(device)

        // Clear failed attempts for this IP on success.
        failedAttempts.removeValue(forKey: clientIP)

        // Regenerate PIN (one-time use).
        regeneratePin()

        let tokenCount = self.tokens.count
        let logMsg = "Device authenticated: \(displayName) from \(clientIP) "
            + "deviceId=\(deviceId) totalTokens=\(tokenCount)"
        Logger.remoteControl.info("\(logMsg, privacy: .public)")

        return .success(AuthTokenResponse(token: token, device: device))
    }

    // MARK: - Token Validation

    /// Validate a bearer token from a subsequent API request.
    ///
    /// SEC-H1: shares the same per-IP failure counter as ``validatePin``.
    /// A blind 64-character hex token has 2^256 possible values, so a brute
    /// force is computationally infeasible — but without rate limiting the
    /// WS endpoint could still be enumerated for *issued* tokens. Sharing
    /// the counter with PIN validation also prevents an attacker from
    /// alternating PIN and token attempts to evade either limit
    /// independently.
    ///
    /// - Parameters:
    ///   - token: The opaque token string.
    ///   - clientIP: The requesting client's IP address.
    /// - Returns: The associated `RemoteDevice` on success, or an `AuthError`.
    func validateToken(_ token: String, clientIP: String) -> Result<RemoteDevice, AuthError> {
        // Global lockout — same as PIN validation: server is hard-disabled.
        if isLocked {
            return .failure(.globalLockout)
        }

        // Shared per-IP rate limit.
        if case .limited(let retryAfter) = checkRateLimit(for: clientIP) {
            Logger.remoteControl.warning("Token validation rate-limited for IP: \(clientIP, privacy: .public), retry after \(retryAfter)s")
            return .failure(.rateLimited(retryAfterSeconds: retryAfter))
        }

        guard let entry = tokens[token] else {
            // Unknown token — count as a failed attempt against the IP.
            recordFailure(for: clientIP)
            return .failure(.invalidToken)
        }

        guard now() < entry.expiresAt else {
            // Token expired -- remove it. Not counted as an attack signal:
            // honest clients hit this once their token TTL elapses.
            // P0-2 fix: previously only `removeDevice` was called here, which
            // cleaned `connectedDevices` but left `activeBridges` with a stale
            // entry and the underlying WS channel open. Now we fire `onTokenExpired`
            // so the server can drive the canonical full teardown (`disconnect(_:)`)
            // which removes the bridge, revokes the device and closes the channel
            // — keeping both registries in sync.
            tokens.removeValue(forKey: token)
            let expiredDeviceId = entry.deviceId
            if onTokenExpired != nil {
                onTokenExpired?(expiredDeviceId)
            } else {
                // Fallback when no server is wired (e.g. tests): clean auth state only.
                removeDevice(expiredDeviceId)
            }
            return .failure(.tokenExpired)
        }

        guard entry.clientIP == clientIP else {
            Logger.remoteControl.warning(
                "Token IP mismatch: expected \(entry.clientIP, privacy: .public), got \(clientIP, privacy: .public)"
            )
            // IP mismatch is a strong attack signal — a leaked token replayed
            // from a different host. Count it.
            recordFailure(for: clientIP)
            return .failure(.ipMismatch)
        }

        guard let device = connectedDevices.first(where: { $0.id == entry.deviceId }) else {
            // Token references a revoked device — treat as invalid token.
            recordFailure(for: clientIP)
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

    /// Result of a per-IP rate-limit check.
    private enum RateLimitStatus {
        case ok
        case limited(retryAfterSeconds: Int)
    }

    /// Inspect the per-IP failure window and decide whether the caller
    /// should be allowed through. Used by both ``validatePin`` and
    /// ``validateToken`` so the limits aggregate across both surfaces
    /// (preventing alternating-attack evasion — see SEC-H1).
    private func checkRateLimit(for clientIP: String) -> RateLimitStatus {
        pruneExpiredAttempts(for: clientIP)
        let recentFailures = failedAttempts[clientIP] ?? []
        guard recentFailures.count >= Constants.maxFailuresPerIP else {
            return .ok
        }
        let oldestInWindow = recentFailures.first ?? now()
        let retryAfter = Int(Constants.rateLimitWindowSeconds - now().timeIntervalSince(oldestInWindow))
        return .limited(retryAfterSeconds: max(retryAfter, 1))
    }

    /// Record a failed authentication attempt for rate-limiting purposes.
    private func recordFailure(for clientIP: String) {
        failedAttempts[clientIP, default: []].append(now())
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
        let cutoff = now().addingTimeInterval(-Constants.rateLimitWindowSeconds)
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

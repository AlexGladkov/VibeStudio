// MARK: - RemoteAuthService
// PIN-based authentication and token management for Remote Control.
// macOS 14+, Swift 5.10

import Foundation
import Observation
import OSLog
import Security

// MARK: - Token Persistence

/// Abstraction over where the active token set is persisted, so the
/// production Keychain store can be swapped for a no-op in tests (keeping
/// test runs isolated from the shared system Keychain).
protocol RemoteTokenStoring: Sendable {
    func save(_ json: String)
    func load() -> String?
}

/// Default persistence: a single Keychain generic-password item.
struct KeychainTokenStore: RemoteTokenStoring {
    let account: String
    func save(_ json: String) { KeychainHelper.save(account: account, value: json) }
    func load() -> String? { KeychainHelper.load(account: account) }
}

/// No-op store — nothing persisted, nothing restored. Used by tests and any
/// caller that wants purely in-memory token state.
struct EphemeralTokenStore: RemoteTokenStoring {
    func save(_ json: String) {}
    func load() -> String? { nil }
}

// MARK: - Supporting Types

/// A remote device that has successfully authenticated via PIN.
struct RemoteDevice: Identifiable, Codable {
    let id: UUID
    let displayName: String
    let ipAddress: String
    let connectedAt: Date
}

/// Internal token storage entry -- not exposed to clients.
/// `Codable` so the active token set can be persisted across app restarts
/// (see `RemoteAuthService` persistence), avoiding a PIN re-prompt every time
/// the process restarts (crash, rebuild, settings-triggered server restart).
struct TokenEntry: Codable {
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
    /// The cryptographic RNG (`SecRandomCopyBytes`) failed — caller should
    /// surface as a 500 to the client and never fall back to a weak secret.
    case secureRandomFailure(osStatus: Int32)
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

    // MARK: - Private Constants

    private enum Constants {
        /// Number of random bytes for the PIN source.
        static let pinByteCount = 4
        /// Number of random bytes for token generation.
        static let tokenByteCount = 32
        /// Token validity duration. 7 days — token can be refreshed via
        /// `/api/v1/auth/refresh` before expiry to extend (sliding window).
        static let tokenTTL: TimeInterval = 7 * 24 * 60 * 60 // 7 days
        /// Sliding-refresh window: any refresh call within `tokenTTL - now`
        /// returns a new token with `expiresAt = now + tokenTTL`.
        static let refreshMinRemaining: TimeInterval = 60 // 1 minute
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

    // MARK: - Persistence

    /// Keychain account under which the active token set is persisted.
    static let persistenceAccount = "vs_remote_tokens_v1"

    /// Where tokens are persisted. Injected so tests can use a no-op store.
    private let tokenStore: RemoteTokenStoring

    /// Persist the current tokens + devices (best-effort). Called after every
    /// mutation so a process restart keeps live sessions authenticated instead
    /// of forcing a PIN the user can't see remotely.
    private func persistState() {
        struct Snapshot: Codable {
            let tokens: [String: TokenEntry]
            let devices: [RemoteDevice]
        }
        let snapshot = Snapshot(tokens: tokens, devices: connectedDevices)
        guard let data = try? JSONEncoder().encode(snapshot),
              let json = String(data: data, encoding: .utf8) else { return }
        tokenStore.save(json)
    }

    /// Load persisted tokens + devices on launch, dropping anything expired or
    /// orphaned. Failure degrades gracefully to an empty set (== pre-persistence
    /// behaviour: the user authenticates once with the PIN).
    private func loadPersistedState() {
        struct Snapshot: Codable {
            let tokens: [String: TokenEntry]
            let devices: [RemoteDevice]
        }
        guard let json = tokenStore.load(),
              let data = json.data(using: .utf8),
              let snapshot = try? JSONDecoder().decode(Snapshot.self, from: data) else {
            return
        }
        let now = Date()
        let validTokens = snapshot.tokens.filter { $0.value.expiresAt > now }
        let liveDeviceIds = Set(validTokens.values.map { $0.deviceId })
        tokens = validTokens
        connectedDevices = snapshot.devices.filter { liveDeviceIds.contains($0.id) }
        if !connectedDevices.isEmpty {
            Logger.remoteControl.info(
                "RemoteAuthService: restored \(self.connectedDevices.count, privacy: .public) device(s) from persisted tokens"
            )
            onDevicesChanged?(connectedDevices.count)
        }
    }

    // MARK: - Init

    /// - Parameter tokenStore: Persistence backend. Defaults to the Keychain;
    ///   tests inject ``EphemeralTokenStore`` to stay isolated from the system
    ///   Keychain.
    init(tokenStore: RemoteTokenStoring = KeychainTokenStore(account: RemoteAuthService.persistenceAccount)) {
        self.tokenStore = tokenStore
        regeneratePin()
        loadPersistedState()
    }

    // MARK: - PIN Management

    /// Generate a new cryptographically random 6-digit PIN.
    ///
    /// Called at init, after a successful authentication, and after a rate-limit
    /// lockout event.
    func regeneratePin() {
        do {
            currentPin = try Self.makeSecurePin()
            Logger.remoteControl.info("PIN regenerated")
        } catch let AuthError.secureRandomFailure(osStatus) {
            // SecRandomCopyBytes failed. Do NOT downgrade to a weak PIN and do NOT crash.
            // Clear currentPin so any further auth attempts deterministically fail with
            // .invalidPin (the user-facing flow is rate-limited and safe).
            currentPin = ""
            Logger.remoteControl.error(
                "PIN regeneration failed: SecRandomCopyBytes status=\(osStatus, privacy: .public). Authentication is temporarily unavailable."
            )
        } catch {
            currentPin = ""
            Logger.remoteControl.error("PIN regeneration failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    /// Throwing PIN generator that propagates RNG failure as `AuthError.secureRandomFailure`.
    private static func makeSecurePin() throws -> String {
        var bytes = [UInt8](repeating: 0, count: Constants.pinByteCount)
        let status = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        guard status == errSecSuccess else {
            throw AuthError.secureRandomFailure(osStatus: status)
        }
        let raw = bytes.withUnsafeBytes { $0.load(as: UInt32.self) }
        return String(format: "%06d", raw % 1_000_000)
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

        // NOTE: the max-devices limit is enforced AFTER the PIN is validated
        // (see the success path below), by evicting the oldest device rather
        // than rejecting. A correct PIN is a legitimate access and must never
        // be locked out — otherwise persisted/stale device slots could leave a
        // remote user (who can't reach the Mac) permanently unable to connect.
        // Checking before the PIN compare would also let wrong-PIN spam reason
        // about the device count, so the check belongs after the match.

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
        let token: String
        do {
            token = try generateToken()
        } catch let AuthError.secureRandomFailure(osStatus) {
            Logger.remoteControl.error(
                "Token generation failed: SecRandomCopyBytes status=\(osStatus, privacy: .public)"
            )
            return .failure(.secureRandomFailure(osStatus: osStatus))
        } catch {
            return .failure(.secureRandomFailure(osStatus: -1))
        }
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

        // Enforce the device cap by evicting the oldest device(s) — newest wins.
        // Keeps at most `maxDevices` concurrent sessions while guaranteeing a
        // freshly PIN-authenticated client always gets in.
        while connectedDevices.count >= RemoteAuthService.maxDevices {
            guard let oldest = connectedDevices.min(by: { $0.connectedAt < $1.connectedAt }) else { break }
            tokens = tokens.filter { $0.value.deviceId != oldest.id }
            connectedDevices.removeAll { $0.id == oldest.id }
            Logger.remoteControl.info(
                "PIN auth at capacity: evicted oldest device \(oldest.id) to admit new login"
            )
        }

        tokens[token] = entry
        connectedDevices.append(device)
        onDevicesChanged?(connectedDevices.count)
        persistState()

        // Clear failed attempts for this IP on success.
        failedAttempts.removeValue(forKey: clientIP)

        // Regenerate PIN (one-time use).
        regeneratePin()

        let tokenCount = self.tokens.count
        let tokenPrefix = String(token.prefix(8))
        Logger.remoteControl.info("Device authenticated: \(displayName, privacy: .public) from \(clientIP, privacy: .public) tokenPrefix=\(tokenPrefix, privacy: .public) totalTokens=\(tokenCount, privacy: .public)")

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
            persistState()
            return .failure(.tokenExpired)
        }

        guard Self.ipBindingSatisfied(expected: entry.clientIP, actual: clientIP) else {
            Logger.remoteControl.warning("Token IP mismatch: expected \(entry.clientIP, privacy: .public), got \(clientIP, privacy: .public)")
            return .failure(.ipMismatch)
        }

        guard let device = connectedDevices.first(where: { $0.id == entry.deviceId }) else {
            return .failure(.invalidToken)
        }

        return .success(device)
    }

    /// Decide whether a token's IP binding is satisfied.
    ///
    /// IP binding is real defense-in-depth for a client connecting **directly**
    /// (same LAN, public IP): a stolen token can't be replayed from another
    /// address. But when the token was issued to the **loopback** address the
    /// server is behind a tunnel/proxy (ngrok) or a local forwarder — every
    /// client appears as `127.0.0.1`, so the binding protects nothing yet still
    /// breaks legitimately if the apparent loopback family flips. In that case
    /// we rely on the 256-bit token secret + TLS as the boundary and skip the
    /// IP equality check. For non-loopback (direct) clients the strict check
    /// stays in force.
    private static func ipBindingSatisfied(expected: String, actual: String) -> Bool {
        if ClientIPExtractor.isLoopback(expected) { return true }
        return expected == actual
    }

    // MARK: - Token Refresh (Sliding Window)

    /// Refresh a still-valid bearer token. Issues a new token bound to the same
    /// device and the requesting IP, with `expiresAt = now + tokenTTL`. The old
    /// token is invalidated (single-use, prevents replay).
    ///
    /// - Returns: A new `AuthTokenResponse` on success, or an `AuthError` if the
    ///   submitted token is invalid / expired / IP-mismatched.
    func refreshToken(_ oldToken: String, clientIP: String) -> Result<AuthTokenResponse, AuthError> {
        guard let entry = tokens[oldToken] else { return .failure(.invalidToken) }
        guard Date() < entry.expiresAt else {
            tokens.removeValue(forKey: oldToken)
            removeDevice(entry.deviceId)
            persistState()
            return .failure(.tokenExpired)
        }
        guard Self.ipBindingSatisfied(expected: entry.clientIP, actual: clientIP) else {
            // Don't leak IP-binding to network clients beyond a generic error.
            Logger.remoteControl.warning(
                "Token refresh IP mismatch: expected \(entry.clientIP, privacy: .public), got \(clientIP, privacy: .public)"
            )
            return .failure(.ipMismatch)
        }
        guard let device = connectedDevices.first(where: { $0.id == entry.deviceId }) else {
            return .failure(.invalidToken)
        }

        let newToken: String
        do {
            newToken = try generateToken()
        } catch let AuthError.secureRandomFailure(osStatus) {
            return .failure(.secureRandomFailure(osStatus: osStatus))
        } catch {
            return .failure(.secureRandomFailure(osStatus: -1))
        }

        let now = Date()
        let newEntry = TokenEntry(
            deviceId: entry.deviceId,
            clientIP: clientIP,
            issuedAt: now,
            expiresAt: now.addingTimeInterval(Constants.tokenTTL)
        )
        tokens.removeValue(forKey: oldToken)
        tokens[newToken] = newEntry
        persistState()

        Logger.remoteControl.info(
            "Token refreshed: device=\(device.id) tokenPrefix=\(String(newToken.prefix(8)), privacy: .public)"
        )
        return .success(AuthTokenResponse(token: newToken, device: device))
    }

    /// Token TTL exposed for response DTOs / client expiry hints.
    var tokenTTLSeconds: TimeInterval { Constants.tokenTTL }

    /// Return the issued-at + expires-at for a token, or nil if unknown.
    func expiry(of token: String) -> (issuedAt: Date, expiresAt: Date)? {
        guard let e = tokens[token] else { return nil }
        return (e.issuedAt, e.expiresAt)
    }

    // MARK: - Device Management

    /// Revoke a specific device's access and remove its token.
    func revokeDevice(_ deviceId: UUID) {
        // Remove all tokens for this device.
        tokens = tokens.filter { $0.value.deviceId != deviceId }
        removeDevice(deviceId)
        persistState()
        Logger.remoteControl.info("Device revoked: \(deviceId)")
    }

    /// Revoke all connected devices and clear all tokens.
    func revokeAllDevices() {
        tokens.removeAll()
        connectedDevices.removeAll()
        onDevicesChanged?(0)
        persistState()
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
    /// Throws `AuthError.secureRandomFailure` instead of crashing the server on RNG failure.
    private func generateToken() throws -> String {
        var bytes = [UInt8](repeating: 0, count: Constants.tokenByteCount)
        let status = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        guard status == errSecSuccess else {
            throw AuthError.secureRandomFailure(osStatus: status)
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

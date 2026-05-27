package studio.vibe.shared.service.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// ── Supporting Types ──────────────────────────────────────────────────────────

/**
 * A remote device that has successfully authenticated via PIN.
 */
@OptIn(ExperimentalUuidApi::class)
data class RemoteDevice(
    val id: Uuid,
    val displayName: String,
    val ipAddress: String,
    val connectedAt: Instant,
)

/**
 * Internal token storage entry — not exposed to clients.
 */
@OptIn(ExperimentalUuidApi::class)
data class TokenEntry(
    val deviceId: Uuid,
    val clientIP: String,
    val issuedAt: Instant,
    val expiresAt: Instant,
)

/**
 * Successful authentication response.
 */
@OptIn(ExperimentalUuidApi::class)
data class AuthTokenResponse(
    val token: String,
    val device: RemoteDevice,
)

/**
 * Authentication errors with machine-readable context.
 */
sealed class AuthError : Exception() {
    data object InvalidPin : AuthError()
    data class RateLimited(val retryAfterSeconds: Int) : AuthError()
    data object GlobalLockout : AuthError()
    data object InvalidToken : AuthError()
    data object TokenExpired : AuthError()
    data object IpMismatch : AuthError()
    data object MaxDevicesReached : AuthError()
}

// ── SecureRandom Interface ────────────────────────────────────────────────────

/**
 * Platform-injectable source of cryptographically secure random bytes.
 *
 * The default implementation ([KotlinSecureRandom]) delegates to [kotlin.random.Random.Default],
 * which is sufficient for most platforms. Platform-specific targets (JVM, iOS) may substitute
 * a stronger implementation backed by `SecureRandom` / `SecRandomCopyBytes`.
 */
interface SecureRandom {
    fun nextBytes(size: Int): ByteArray
}

/**
 * Default [SecureRandom] backed by [kotlin.random.Random.Default].
 *
 * This is adequate for development and testing. Production deployments on security-sensitive
 * targets should inject a platform-specific implementation backed by OS-level CSPRNG.
 */
object KotlinSecureRandom : SecureRandom {
    override fun nextBytes(size: Int): ByteArray = Random.Default.nextBytes(size)
}

// ── RemoteAuthServiceImpl ─────────────────────────────────────────────────────

/**
 * Manages PIN generation, token issuance, rate limiting, and device tracking
 * for the Remote Control server.
 *
 * Security invariants:
 * - PIN is a 6-digit value derived from [SecureRandom.nextBytes] (4 bytes → UInt32 mod 1_000_000).
 * - PIN is one-time-use: consumed on successful validation, then regenerated automatically.
 * - Token is [TOKEN_BYTE_COUNT] random bytes hex-encoded, IP-bound, [TOKEN_TTL] TTL.
 * - Per-IP rate limit: [MAX_FAILURES_PER_IP] failures within [RATE_LIMIT_WINDOW] = IP lockout.
 * - Global rate limit: [GLOBAL_LOCKOUT_THRESHOLD] total failures = [isLocked] = server auto-disables.
 *
 * Thread-safety: The original Swift source was @MainActor (single-threaded dispatch queue).
 * This class carries the same invariant: **all public methods must be called from a single,
 * confined coroutine dispatcher** (e.g. Dispatchers.Main or a single-threaded custom dispatcher).
 * No internal synchronisation is applied — correctness depends on call-site confinement.
 */
@OptIn(ExperimentalUuidApi::class)
class RemoteAuthServiceImpl(
    private val secureRandom: SecureRandom = KotlinSecureRandom,
) {

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        /** Maximum number of simultaneously connected remote devices. */
        const val MAX_DEVICES = 3

        /** Number of random bytes used as the PIN source (4 bytes → UInt32 → 6-digit mod). */
        private const val PIN_BYTE_COUNT = 4

        /** Number of random bytes for token generation. */
        private const val TOKEN_BYTE_COUNT = 32

        /** Token validity duration. */
        private val TOKEN_TTL = 4.hours

        /** Maximum failed attempts per IP within the rate-limit window. */
        private const val MAX_FAILURES_PER_IP = 3

        /** Sliding-window duration for per-IP rate limiting. */
        private val RATE_LIMIT_WINDOW = 5.minutes

        /**
         * Global failure threshold — after this many total failures across all IPs the
         * server locks out completely until [resetLockout] is called manually.
         */
        private const val GLOBAL_LOCKOUT_THRESHOLD = 10

        /**
         * When the per-IP failure dictionary exceeds this entry count, a full sweep of
         * all entries is performed to prevent unbounded growth from unique attacker IPs.
         */
        private const val FULL_PRUNE_THRESHOLD = 1000
    }

    // ── Observable State ──────────────────────────────────────────────────────

    private val _currentPin = MutableStateFlow("")
    /** The current 6-digit PIN displayed to the user. */
    val currentPin: StateFlow<String> = _currentPin.asStateFlow()

    private val _connectedDevices = MutableStateFlow<List<RemoteDevice>>(emptyList())
    /** Currently authenticated and connected remote devices. */
    val connectedDevices: StateFlow<List<RemoteDevice>> = _connectedDevices.asStateFlow()

    private val _isLocked = MutableStateFlow(false)
    /**
     * When `true`, the server is globally locked out due to excessive failed authentication
     * attempts. Must be reset manually via [resetLockout].
     */
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    /** Called when the server should shut down due to excessive failed auth attempts. */
    var onSecurityLockout: (() -> Unit)? = null

    /** Called when the connected devices list changes (add/remove). */
    var onDevicesChanged: ((count: Int) -> Unit)? = null

    // ── Private State ─────────────────────────────────────────────────────────

    /** Active tokens: opaque hex-string → [TokenEntry]. */
    private val tokens = mutableMapOf<String, TokenEntry>()

    /** Per-IP failed attempt timestamps for sliding-window rate limiting. */
    private val failedAttempts = mutableMapOf<String, MutableList<Instant>>()

    /** Total failed attempts across all IPs (never reset automatically). */
    private var globalFailedCount = 0

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        regeneratePin()
    }

    // ── PIN Management ────────────────────────────────────────────────────────

    /**
     * Generate a new 6-digit PIN from [SecureRandom.nextBytes].
     *
     * Called at init, after a successful authentication, and after [resetLockout].
     * 4 random bytes are loaded as a little-endian UInt32 and taken modulo 1_000_000
     * to produce a uniformly distributed 6-digit value.
     */
    fun regeneratePin() {
        val bytes = secureRandom.nextBytes(PIN_BYTE_COUNT)
        // Interpret 4 bytes as a 32-bit unsigned integer (little-endian) then mod 1_000_000.
        val raw = ((bytes[0].toInt() and 0xFF) or
                ((bytes[1].toInt() and 0xFF) shl 8) or
                ((bytes[2].toInt() and 0xFF) shl 16) or
                ((bytes[3].toInt() and 0xFF) shl 24)).toLong() and 0xFFFFFFFFL
        val pin = (raw % 1_000_000L).toInt()
        _currentPin.value = pin.toString().padStart(6, '0')
    }

    // ── PIN Validation ────────────────────────────────────────────────────────

    /**
     * Validate a PIN submitted by a remote client.
     *
     * On success, the PIN is consumed (regenerated) and a bearer token is issued.
     * On failure, rate-limiting counters are incremented.
     *
     * @param pin The 6-digit PIN string from the client.
     * @param clientIP The client's IP address for rate limiting and token binding.
     * @param userAgent The client's User-Agent header for device display name.
     * @return [AuthTokenResponse] on success wrapped in [Result.success],
     *   or an [AuthError] on failure wrapped in [Result.failure].
     */
    fun validatePin(
        pin: String,
        clientIP: String,
        userAgent: String,
    ): Result<AuthTokenResponse> {
        // Check global lockout first.
        if (_isLocked.value) {
            return Result.failure(AuthError.GlobalLockout)
        }

        // Check max connected devices.
        if (_connectedDevices.value.size >= MAX_DEVICES) {
            return Result.failure(AuthError.MaxDevicesReached)
        }

        // Check per-IP rate limit.
        pruneExpiredAttempts(clientIP)
        val recentFailures = failedAttempts[clientIP] ?: emptyList<Instant>()
        if (recentFailures.size >= MAX_FAILURES_PER_IP) {
            val oldestInWindow = recentFailures.first()
            val elapsed = Clock.System.now() - oldestInWindow
            val retryAfter = (RATE_LIMIT_WINDOW - elapsed).inWholeSeconds.toInt()
            return Result.failure(AuthError.RateLimited(maxOf(retryAfter, 1)))
        }

        // Constant-time comparison to mitigate timing side-channel attacks.
        if (!constantTimeEqual(pin, _currentPin.value)) {
            recordFailure(clientIP)
            return Result.failure(AuthError.InvalidPin)
        }

        // Success — consume PIN and issue token.
        val token = generateToken()
        val deviceId = Uuid.random()
        val now = Clock.System.now()
        val expiresAt = now + TOKEN_TTL
        val displayName = parseDeviceName(userAgent)

        val device = RemoteDevice(
            id = deviceId,
            displayName = displayName,
            ipAddress = clientIP,
            connectedAt = now,
        )

        val entry = TokenEntry(
            deviceId = deviceId,
            clientIP = clientIP,
            issuedAt = now,
            expiresAt = expiresAt,
        )

        tokens[token] = entry
        _connectedDevices.update { it + device }
        onDevicesChanged?.invoke(_connectedDevices.value.size)

        // Clear per-IP failure history on success.
        failedAttempts.remove(clientIP)

        // One-time-use: regenerate immediately.
        regeneratePin()

        return Result.success(AuthTokenResponse(token = token, device = device))
    }

    // ── Token Validation ──────────────────────────────────────────────────────

    /**
     * Validate a bearer token from a subsequent API request.
     *
     * @param token The opaque hex-encoded token string.
     * @param clientIP The requesting client's IP address.
     * @return The associated [RemoteDevice] on success, or an [AuthError] on failure.
     */
    fun validateToken(token: String, clientIP: String): Result<RemoteDevice> {
        val entry = tokens[token]
            ?: return Result.failure(AuthError.InvalidToken)

        val now = Clock.System.now()
        if (now >= entry.expiresAt) {
            tokens.remove(token)
            removeDevice(entry.deviceId)
            return Result.failure(AuthError.TokenExpired)
        }

        if (entry.clientIP != clientIP) {
            return Result.failure(AuthError.IpMismatch)
        }

        val device = _connectedDevices.value.firstOrNull { it.id == entry.deviceId }
            ?: return Result.failure(AuthError.InvalidToken)

        return Result.success(device)
    }

    // ── Device Management ─────────────────────────────────────────────────────

    /**
     * Revoke a specific device's access and remove all its tokens.
     */
    fun revokeDevice(deviceId: Uuid) {
        tokens.entries.removeAll { it.value.deviceId == deviceId }
        removeDevice(deviceId)
    }

    /**
     * Revoke all connected devices and clear all tokens.
     */
    fun revokeAllDevices() {
        tokens.clear()
        _connectedDevices.value = emptyList()
        onDevicesChanged?.invoke(0)
    }

    /**
     * Reset the global lockout state. Must be called manually from the host UI.
     *
     * Clears [isLocked], resets [globalFailedCount], clears all per-IP failure history,
     * and generates a fresh PIN.
     */
    fun resetLockout() {
        _isLocked.value = false
        globalFailedCount = 0
        failedAttempts.clear()
        regeneratePin()
    }

    // ── Private: Token Generation ─────────────────────────────────────────────

    /**
     * Generate a [SecureRandom]-backed [TOKEN_BYTE_COUNT]-byte hex-encoded opaque token.
     */
    private fun generateToken(): String {
        val bytes = secureRandom.nextBytes(TOKEN_BYTE_COUNT)
        return bytes.joinToString("") { byte ->
            byte.toInt().and(0xFF).toString(16).padStart(2, '0')
        }
    }

    // ── Private: Rate Limiting ────────────────────────────────────────────────

    /**
     * Record a failed authentication attempt for rate-limiting purposes.
     *
     * Increments both the per-IP counter and [globalFailedCount]. Triggers
     * [onSecurityLockout] when the global threshold is reached.
     */
    private fun recordFailure(clientIP: String) {
        failedAttempts.getOrPut(clientIP) { mutableListOf() }.add(Clock.System.now())
        globalFailedCount++

        if (globalFailedCount >= GLOBAL_LOCKOUT_THRESHOLD) {
            _isLocked.value = true
            onSecurityLockout?.invoke()
        }
    }

    /**
     * Remove expired entries from the per-IP failure map.
     *
     * Also performs a full sweep when [failedAttempts] exceeds [FULL_PRUNE_THRESHOLD]
     * entries to prevent unbounded dictionary growth from unique attacker IPs.
     */
    private fun pruneExpiredAttempts(clientIP: String) {
        val cutoff = Clock.System.now() - RATE_LIMIT_WINDOW
        failedAttempts[clientIP]?.removeAll { it <= cutoff }
        if (failedAttempts[clientIP]?.isEmpty() == true) {
            failedAttempts.remove(clientIP)
        }

        if (failedAttempts.size > FULL_PRUNE_THRESHOLD) {
            val keys = failedAttempts.keys.toList()
            for (ip in keys) {
                failedAttempts[ip]?.removeAll { it <= cutoff }
                if (failedAttempts[ip]?.isEmpty() == true) {
                    failedAttempts.remove(ip)
                }
            }
        }
    }

    // ── Private: Device Tracking ──────────────────────────────────────────────

    /**
     * Remove a device from [connectedDevices] and notify [onDevicesChanged].
     */
    private fun removeDevice(deviceId: Uuid) {
        _connectedDevices.update { devices -> devices.filter { it.id != deviceId } }
        onDevicesChanged?.invoke(_connectedDevices.value.size)
    }

    // ── Private: Helpers ──────────────────────────────────────────────────────

    /**
     * Constant-time byte-wise comparison to mitigate timing side-channel attacks.
     *
     * Both inputs are compared across the full length of the longer string.
     * Length mismatch is XOR'd into the accumulator, preventing length-based leakage.
     */
    private fun constantTimeEqual(a: String, b: String): Boolean {
        val aBytes = a.encodeToByteArray()
        val bBytes = b.encodeToByteArray()
        val maxLen = maxOf(aBytes.size, bBytes.size)
        if (maxLen == 0) return true
        // XOR length difference into result so mismatched lengths never return true.
        var result: Int = (aBytes.size xor bBytes.size)
        for (i in 0 until maxLen) {
            val aByte = if (i < aBytes.size) aBytes[i].toInt() and 0xFF else 0
            val bByte = if (i < bBytes.size) bBytes[i].toInt() and 0xFF else 0
            result = result or (aByte xor bByte)
        }
        return result == 0
    }

    /**
     * Parse a human-readable device name from a User-Agent string.
     *
     * Examples:
     * - `"Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X)"` → `"iPhone"`
     * - `"Mozilla/5.0 (iPad; ...)"` → `"iPad"`
     * - `"Mozilla/5.0 (Macintosh; ...)"` → `"Mac"`
     * - Unknown → `"Remote Device"`
     */
    private fun parseDeviceName(userAgent: String): String = when {
        userAgent.contains("iPhone") -> "iPhone"
        userAgent.contains("iPad") -> "iPad"
        userAgent.contains("Macintosh") -> "Mac"
        userAgent.contains("Android") -> "Android"
        userAgent.contains("Linux") -> "Linux"
        userAgent.contains("Windows") -> "Windows"
        else -> "Remote Device"
    }
}

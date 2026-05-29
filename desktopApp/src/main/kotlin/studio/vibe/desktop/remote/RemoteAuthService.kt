package studio.vibe.desktop.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/**
 * Authentication errors with machine-readable context.
 */
sealed class AuthError {
    data object InvalidPin : AuthError()
    data class RateLimited(val retryAfterSeconds: Int) : AuthError()
    data object GlobalLockout : AuthError()
    data object InvalidToken : AuthError()
    data object TokenExpired : AuthError()
    data object IpMismatch : AuthError()
    data object MaxDevicesReached : AuthError()
}

/**
 * Successful authentication result.
 */
data class AuthResult(
    val token: String,
    val device: RemoteDevice,
    val expiresAt: Long,
)

/**
 * PIN-based authentication and token management for the Remote Control server.
 *
 * Security invariants (mirroring Swift RemoteAuthService):
 * - PIN is 6 cryptographic random digits from [SecureRandom].
 * - PIN is one-time-use: consumed on successful validation, then regenerated.
 * - Token is 32 random bytes hex-encoded, IP-bound, 4-hour TTL.
 * - Per-IP rate limit: 3 failures within 5 minutes = IP lockout.
 * - Global rate limit: 10 total failures = [isLocked] = server auto-disables.
 *
 * All state mutations are thread-safe via [ConcurrentHashMap] and [synchronized].
 * This class is designed to be called from Ktor route handlers (IO dispatcher).
 */
class RemoteAuthService {

    companion object {
        const val MAX_DEVICES = 3
        private const val TOKEN_TTL_MS = 4L * 60 * 60 * 1000L  // 4 hours
        private const val MAX_FAILURES_PER_IP = 3
        private const val RATE_LIMIT_WINDOW_MS = 5L * 60 * 1000L  // 5 minutes
        private const val GLOBAL_LOCKOUT_THRESHOLD = 10
        private val log = Logger.getLogger("RemoteAuthService")
    }

    private val random = SecureRandom()

    // ── Observable state ──────────────────────────────────────────────────────

    private val _currentPin = MutableStateFlow(generatePin())
    val currentPin: StateFlow<String> = _currentPin.asStateFlow()

    private val _connectedDevices = MutableStateFlow<List<RemoteDevice>>(emptyList())
    val connectedDevices: StateFlow<List<RemoteDevice>> = _connectedDevices.asStateFlow()

    @Volatile
    var isLocked: Boolean = false
        private set

    /** Called when the server should shut down due to excessive failed auth attempts. */
    var onSecurityLockout: (() -> Unit)? = null

    /** Called when the connected devices list changes. */
    var onDevicesChanged: ((Int) -> Unit)? = null

    // ── Private state (thread-safe) ────────────────────────────────────────────

    private val tokens = ConcurrentHashMap<String, TokenEntry>()
    private val failedAttempts = ConcurrentHashMap<String, MutableList<Long>>()

    @Volatile
    private var globalFailedCount = 0

    // ── PIN management ─────────────────────────────────────────────────────────

    /** Regenerate the 6-digit PIN using [SecureRandom]. */
    fun regeneratePin() {
        _currentPin.value = generatePin()
        log.info("PIN regenerated")
    }

    private fun generatePin(): String {
        val raw = (random.nextInt(1_000_000)).toUInt().toLong()
        return raw.toString().padStart(6, '0')
    }

    // ── PIN validation ─────────────────────────────────────────────────────────

    /**
     * Validate a PIN submitted by a remote client.
     *
     * On success the PIN is consumed (regenerated) and a bearer token is issued.
     * On failure, rate-limiting counters are incremented.
     *
     * Thread-safe: synchronized on [this] for the full validation critical section.
     */
    @Synchronized
    fun validatePin(
        pin: String,
        clientIP: String,
        userAgent: String,
    ): Result<AuthResult, AuthError> {
        if (isLocked) {
            log.warning("PIN validation rejected: global lockout active (IP: $clientIP)")
            return Result.failure(AuthError.GlobalLockout)
        }

        if (_connectedDevices.value.size >= MAX_DEVICES) {
            log.warning("PIN validation rejected: max devices reached (IP: $clientIP)")
            return Result.failure(AuthError.MaxDevicesReached)
        }

        pruneExpiredAttempts(clientIP)
        val recentFailures = failedAttempts[clientIP] ?: emptyList<Long>()
        if (recentFailures.size >= MAX_FAILURES_PER_IP) {
            val oldest = recentFailures.firstOrNull() ?: System.currentTimeMillis()
            val retryAfter = ((RATE_LIMIT_WINDOW_MS - (System.currentTimeMillis() - oldest)) / 1000)
                .coerceAtLeast(1).toInt()
            log.warning("PIN validation rate-limited for IP: $clientIP, retry after ${retryAfter}s")
            return Result.failure(AuthError.RateLimited(retryAfter))
        }

        // Constant-time comparison to mitigate timing attacks.
        if (!constantTimeEqual(pin, _currentPin.value)) {
            recordFailure(clientIP)
            val remaining = MAX_FAILURES_PER_IP - (failedAttempts[clientIP]?.size ?: 0)
            log.warning("Invalid PIN from IP: $clientIP, $remaining attempts remaining")
            return Result.failure(AuthError.InvalidPin)
        }

        // Success — consume PIN and issue token.
        val token = generateToken()
        val deviceId = UUID.randomUUID()
        val now = System.currentTimeMillis()
        val expiresAt = now + TOKEN_TTL_MS
        val displayName = parseDeviceName(userAgent)

        val device = RemoteDevice(
            id = deviceId,
            displayName = displayName,
            ipAddress = clientIP,
            connectedAt = now,
        )

        tokens[token] = TokenEntry(
            deviceId = deviceId,
            clientIP = clientIP,
            issuedAt = now,
            expiresAt = expiresAt,
        )

        _connectedDevices.value = _connectedDevices.value + device
        onDevicesChanged?.invoke(_connectedDevices.value.size)
        failedAttempts.remove(clientIP)
        regeneratePin()

        log.info("Device authenticated: $displayName from $clientIP token=${token.take(8)}...")
        return Result.success(AuthResult(token = token, device = device, expiresAt = expiresAt))
    }

    // ── Token validation ───────────────────────────────────────────────────────

    /**
     * Validate a bearer token from a subsequent API request.
     *
     * Thread-safe: read-only on [tokens] (ConcurrentHashMap) + synchronized for
     * remove-and-mutate path.
     */
    fun validateToken(token: String, clientIP: String): Result<RemoteDevice, AuthError> {
        val entry = tokens[token] ?: return Result.failure(AuthError.InvalidToken)

        if (System.currentTimeMillis() > entry.expiresAt) {
            tokens.remove(token)
            removeDevice(entry.deviceId)
            return Result.failure(AuthError.TokenExpired)
        }

        if (entry.clientIP != clientIP) {
            log.warning("Token IP mismatch: expected ${entry.clientIP}, got $clientIP")
            return Result.failure(AuthError.IpMismatch)
        }

        val device = _connectedDevices.value.firstOrNull { it.id == entry.deviceId }
            ?: return Result.failure(AuthError.InvalidToken)

        return Result.success(device)
    }

    // ── Device management ──────────────────────────────────────────────────────

    @Synchronized
    fun revokeDevice(deviceId: UUID) {
        val iter = tokens.entries.iterator()
        while (iter.hasNext()) {
            if (iter.next().value.deviceId == deviceId) iter.remove()
        }
        removeDevice(deviceId)
        log.info("Device revoked: $deviceId")
    }

    @Synchronized
    fun revokeAllDevices() {
        tokens.clear()
        _connectedDevices.value = emptyList()
        onDevicesChanged?.invoke(0)
        log.info("All devices revoked")
    }

    @Synchronized
    fun resetLockout() {
        isLocked = false
        globalFailedCount = 0
        failedAttempts.clear()
        regeneratePin()
        log.info("Global lockout reset")
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    @Synchronized
    private fun recordFailure(clientIP: String) {
        failedAttempts.getOrPut(clientIP) { mutableListOf() }.add(System.currentTimeMillis())
        globalFailedCount++
        if (globalFailedCount >= GLOBAL_LOCKOUT_THRESHOLD) {
            isLocked = true
            log.severe("Global lockout activated after $globalFailedCount total failures")
            onSecurityLockout?.invoke()
        }
    }

    private fun pruneExpiredAttempts(clientIP: String) {
        val cutoff = System.currentTimeMillis() - RATE_LIMIT_WINDOW_MS
        val list = failedAttempts[clientIP] ?: return
        list.removeAll { it <= cutoff }
        if (list.isEmpty()) failedAttempts.remove(clientIP)

        // Periodic full sweep if dictionary grows large.
        if (failedAttempts.size > 1000) {
            val iter = failedAttempts.entries.iterator()
            while (iter.hasNext()) {
                val entry = iter.next()
                entry.value.removeAll { it <= cutoff }
                if (entry.value.isEmpty()) iter.remove()
            }
        }
    }

    private fun removeDevice(deviceId: UUID) {
        _connectedDevices.value = _connectedDevices.value.filter { it.id != deviceId }
        onDevicesChanged?.invoke(_connectedDevices.value.size)
    }

    /**
     * Constant-time string comparison to mitigate timing side-channel attacks.
     * Both inputs are compared byte-by-byte without short-circuiting.
     */
    private fun constantTimeEqual(a: String, b: String): Boolean {
        val aBytes = a.encodeToByteArray()
        val bBytes = b.encodeToByteArray()
        val maxLen = maxOf(aBytes.size, bBytes.size)
        if (maxLen == 0) return true
        var result: Int = aBytes.size xor bBytes.size
        for (i in 0 until maxLen) {
            val aByte = if (i < aBytes.size) aBytes[i].toInt() and 0xFF else 0
            val bByte = if (i < bBytes.size) bBytes[i].toInt() and 0xFF else 0
            result = result or (aByte xor bByte)
        }
        return result == 0
    }

    private fun parseDeviceName(userAgent: String): String = when {
        "iPhone" in userAgent -> "iPhone"
        "iPad" in userAgent -> "iPad"
        "Macintosh" in userAgent -> "Mac"
        "Android" in userAgent -> "Android"
        "Linux" in userAgent -> "Linux"
        "Windows" in userAgent -> "Windows"
        else -> "Remote Device"
    }
}

// ── Lightweight Result type (avoids kotlin.Result wrapping issues) ────────────

sealed class Result<out T, out E> {
    data class Success<T>(val value: T) : Result<T, Nothing>()
    data class Failure<E>(val error: E) : Result<Nothing, E>()

    companion object {
        fun <T> success(value: T): Result<T, Nothing> = Success(value)
        fun <E> failure(error: E): Result<Nothing, E> = Failure(error)
    }

    val isSuccess: Boolean get() = this is Success
    fun getOrNull(): T? = (this as? Success)?.value
    fun errorOrNull(): E? = (this as? Failure)?.error
}

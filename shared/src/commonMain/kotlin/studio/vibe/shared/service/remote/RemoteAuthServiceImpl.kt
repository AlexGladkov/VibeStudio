package studio.vibe.shared.service.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import studio.vibe.shared.contract.RemoteAuthorizing
import studio.vibe.shared.model.RemoteAuthError
import studio.vibe.shared.model.RemoteAuthResult
import studio.vibe.shared.model.RemoteAuthorizationToken
import studio.vibe.shared.model.RemoteDevice

// ── SecureRandom Interface ────────────────────────────────────────────────────

/**
 * Platform-injectable source of cryptographically secure random bytes.
 *
 * Default [KotlinSecureRandom] delegates to [kotlin.random.Random.Default].
 * Platforms with stricter requirements should inject an implementation
 * backed by `java.security.SecureRandom` / `SecRandomCopyBytes`.
 */
interface SecureRandom {
    fun nextBytes(size: Int): ByteArray
}

object KotlinSecureRandom : SecureRandom {
    override fun nextBytes(size: Int): ByteArray = Random.Default.nextBytes(size)
}

// ── Internal token storage ────────────────────────────────────────────────────

@OptIn(ExperimentalUuidApi::class)
internal data class TokenEntry(
    val deviceId: Uuid,
    val clientIP: String,
    val issuedAt: Instant,
    val expiresAt: Instant,
)

// ── RemoteAuthServiceImpl ─────────────────────────────────────────────────────

/**
 * Manages PIN generation, token issuance, rate limiting and device tracking
 * for the Remote Control server.
 *
 * Security invariants:
 * - PIN is a 6-digit value derived from [SecureRandom.nextBytes] (4 bytes → UInt32 mod 1_000_000).
 * - PIN is one-time-use: consumed on successful validation, regenerated automatically.
 * - Token is [TOKEN_BYTE_COUNT] random bytes hex-encoded, IP-bound, [TOKEN_TTL] TTL.
 * - Per-IP rate limit: [MAX_FAILURES_PER_IP] failures within [RATE_LIMIT_WINDOW].
 * - Global rate limit: [GLOBAL_LOCKOUT_THRESHOLD] total failures → [isLocked].
 *
 * Thread-safety: all mutating operations are guarded by [mutex] so the
 * implementation is safe to call from concurrent dispatchers (e.g. multiple
 * Ktor request handlers).
 */
@OptIn(ExperimentalUuidApi::class)
class RemoteAuthServiceImpl(
    private val secureRandom: SecureRandom = KotlinSecureRandom,
) : RemoteAuthorizing {

    companion object {
        const val MAX_DEVICES = 3
        private const val PIN_BYTE_COUNT = 4
        private const val TOKEN_BYTE_COUNT = 32
        private val TOKEN_TTL = 4.hours
        private const val MAX_FAILURES_PER_IP = 3
        private val RATE_LIMIT_WINDOW = 5.minutes
        private const val GLOBAL_LOCKOUT_THRESHOLD = 10
        private const val FULL_PRUNE_THRESHOLD = 1000
    }

    private val mutex = Mutex()

    private val _currentPin = MutableStateFlow("")
    override val currentPin: StateFlow<String> = _currentPin.asStateFlow()

    private val _connectedDevices = MutableStateFlow<List<RemoteDevice>>(emptyList())
    override val connectedDevices: StateFlow<List<RemoteDevice>> = _connectedDevices.asStateFlow()

    private val _isLocked = MutableStateFlow(false)
    override val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    override val maxDevices: Int = MAX_DEVICES

    override var onSecurityLockout: (() -> Unit)? = null
    override var onDevicesChanged: ((Int) -> Unit)? = null

    private val tokens = mutableMapOf<String, TokenEntry>()
    private val failedAttempts = mutableMapOf<String, MutableList<Instant>>()
    private var globalFailedCount = 0

    init {
        regeneratePin()
    }

    override fun regeneratePin() {
        val bytes = secureRandom.nextBytes(PIN_BYTE_COUNT)
        val raw = ((bytes[0].toInt() and 0xFF) or
                ((bytes[1].toInt() and 0xFF) shl 8) or
                ((bytes[2].toInt() and 0xFF) shl 16) or
                ((bytes[3].toInt() and 0xFF) shl 24)).toLong() and 0xFFFFFFFFL
        val pin = (raw % 1_000_000L).toInt()
        _currentPin.value = pin.toString().padStart(6, '0')
    }

    override suspend fun validatePin(
        pin: String,
        clientIP: String,
        userAgent: String,
    ): RemoteAuthResult<RemoteAuthorizationToken> = mutex.withLock {
        if (_isLocked.value) return@withLock RemoteAuthResult.failure(RemoteAuthError.GlobalLockout)
        if (_connectedDevices.value.size >= MAX_DEVICES) {
            return@withLock RemoteAuthResult.failure(RemoteAuthError.MaxDevicesReached)
        }

        pruneExpiredAttempts(clientIP)
        val recentFailures = failedAttempts[clientIP] ?: emptyList<Instant>()
        if (recentFailures.size >= MAX_FAILURES_PER_IP) {
            val oldestInWindow = recentFailures.first()
            val elapsed = Clock.System.now() - oldestInWindow
            val retryAfter = (RATE_LIMIT_WINDOW - elapsed).inWholeSeconds.toInt()
            return@withLock RemoteAuthResult.failure(
                RemoteAuthError.RateLimited(maxOf(retryAfter, 1)),
            )
        }

        if (!constantTimeEqual(pin, _currentPin.value)) {
            recordFailure(clientIP)
            return@withLock RemoteAuthResult.failure(RemoteAuthError.InvalidPin)
        }

        val token = generateToken()
        val deviceId = Uuid.random()
        val now = Clock.System.now()
        val expiresAt = now + TOKEN_TTL

        val device = RemoteDevice(
            id = deviceId,
            displayName = parseDeviceName(userAgent),
            ipAddress = clientIP,
            connectedAt = now,
        )

        tokens[token] = TokenEntry(
            deviceId = deviceId,
            clientIP = clientIP,
            issuedAt = now,
            expiresAt = expiresAt,
        )
        _connectedDevices.update { it + device }
        onDevicesChanged?.invoke(_connectedDevices.value.size)
        failedAttempts.remove(clientIP)
        regeneratePin()

        RemoteAuthResult.success(
            RemoteAuthorizationToken(token = token, device = device, expiresAt = expiresAt),
        )
    }

    override suspend fun validateToken(
        token: String,
        clientIP: String,
    ): RemoteAuthResult<RemoteDevice> = mutex.withLock {
        val entry = tokens[token] ?: return@withLock RemoteAuthResult.failure(RemoteAuthError.InvalidToken)

        if (Clock.System.now() >= entry.expiresAt) {
            tokens.remove(token)
            removeDeviceInternal(entry.deviceId)
            return@withLock RemoteAuthResult.failure(RemoteAuthError.TokenExpired)
        }

        if (entry.clientIP != clientIP) {
            return@withLock RemoteAuthResult.failure(RemoteAuthError.IpMismatch)
        }

        val device = _connectedDevices.value.firstOrNull { it.id == entry.deviceId }
            ?: return@withLock RemoteAuthResult.failure(RemoteAuthError.InvalidToken)

        RemoteAuthResult.success(device)
    }

    override suspend fun revokeDevice(deviceId: Uuid): Unit = mutex.withLock {
        tokens.entries.removeAll { it.value.deviceId == deviceId }
        removeDeviceInternal(deviceId)
    }

    override suspend fun revokeAllDevices(): Unit = mutex.withLock {
        tokens.clear()
        _connectedDevices.value = emptyList()
        onDevicesChanged?.invoke(0)
    }

    override suspend fun resetLockout(): Unit = mutex.withLock {
        _isLocked.value = false
        globalFailedCount = 0
        failedAttempts.clear()
        regeneratePin()
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun generateToken(): String {
        val bytes = secureRandom.nextBytes(TOKEN_BYTE_COUNT)
        return bytes.joinToString("") { byte ->
            byte.toInt().and(0xFF).toString(16).padStart(2, '0')
        }
    }

    private fun recordFailure(clientIP: String) {
        failedAttempts.getOrPut(clientIP) { mutableListOf() }.add(Clock.System.now())
        globalFailedCount++

        if (globalFailedCount >= GLOBAL_LOCKOUT_THRESHOLD) {
            _isLocked.value = true
            onSecurityLockout?.invoke()
        }
    }

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

    private fun removeDeviceInternal(deviceId: Uuid) {
        _connectedDevices.update { devices -> devices.filter { it.id != deviceId } }
        onDevicesChanged?.invoke(_connectedDevices.value.size)
    }

    /**
     * Constant-time byte-wise comparison to mitigate timing side-channel attacks.
     */
    private fun constantTimeEqual(a: String, b: String): Boolean {
        val aBytes = a.encodeToByteArray()
        val bBytes = b.encodeToByteArray()
        val maxLen = maxOf(aBytes.size, bBytes.size)
        if (maxLen == 0) return true
        var result: Int = (aBytes.size xor bBytes.size)
        for (i in 0 until maxLen) {
            val aByte = if (i < aBytes.size) aBytes[i].toInt() and 0xFF else 0
            val bByte = if (i < bBytes.size) bBytes[i].toInt() and 0xFF else 0
            result = result or (aByte xor bByte)
        }
        return result == 0
    }

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

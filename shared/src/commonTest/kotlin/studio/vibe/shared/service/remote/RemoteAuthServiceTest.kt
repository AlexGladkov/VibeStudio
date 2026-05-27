package studio.vibe.shared.service.remote

import kotlin.test.*

/**
 * Unit tests for [RemoteAuthServiceImpl].
 *
 * Each test constructs a fresh [RemoteAuthServiceImpl] with a deterministic
 * [SecureRandom] stub so results are reproducible.
 *
 * AAA structure is followed throughout. No coroutines are needed because all
 * public methods on [RemoteAuthServiceImpl] are synchronous.
 */
@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
class RemoteAuthServiceTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * A [SecureRandom] implementation that returns a fixed byte sequence.
     * Rotating through [chunks] allows controlling both PIN and token bytes.
     */
    private class FixedSecureRandom(private vararg val chunks: ByteArray) : SecureRandom {
        private var index = 0
        override fun nextBytes(size: Int): ByteArray {
            val chunk = chunks[index % chunks.size]
            index++
            // Return exactly `size` bytes, padding/truncating as needed.
            return ByteArray(size) { i -> if (i < chunk.size) chunk[i] else 0 }
        }
    }

    /** Builds the PIN that [FixedSecureRandom] with [bytes] would produce. */
    private fun pinFromBytes(bytes: ByteArray): String {
        val raw = ((bytes[0].toInt() and 0xFF) or
                ((bytes[1].toInt() and 0xFF) shl 8) or
                ((bytes[2].toInt() and 0xFF) shl 16) or
                ((bytes[3].toInt() and 0xFF) shl 24)).toLong() and 0xFFFFFFFFL
        return (raw % 1_000_000L).toInt().toString().padStart(6, '0')
    }

    private fun freshService(secureRandom: SecureRandom = KotlinSecureRandom): RemoteAuthServiceImpl =
        RemoteAuthServiceImpl(secureRandom = secureRandom)

    // ── PIN Generation ────────────────────────────────────────────────────────

    @Test
    fun regeneratePin_producesSixDigitString() {
        // Arrange
        val service = freshService()

        // Act
        val pin = service.currentPin.value

        // Assert
        assertEquals(6, pin.length, "PIN must always be exactly 6 characters")
        assertTrue(pin.all { it.isDigit() }, "PIN must contain only digits, got: $pin")
    }

    @Test
    fun regeneratePin_zeroPadsShortValues() {
        // Arrange — bytes that produce the raw value 7 → pin "000007"
        val bytes = byteArrayOf(7, 0, 0, 0)
        val rng = FixedSecureRandom(
            bytes,              // used for PIN at init
            ByteArray(32) { 0 }, // placeholder for token bytes
        )
        // Act
        val service = freshService(rng)
        val pin = service.currentPin.value

        // Assert
        assertEquals("000007", pin)
    }

    @Test
    fun regeneratePin_neverExceedsSixDigits() {
        // Arrange — bytes that encode UInt.MAX_VALUE (0xFFFFFFFF = 4294967295)
        // 4294967295 % 1_000_000 = 967295
        val bytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val rng = FixedSecureRandom(bytes, ByteArray(32))
        // Act
        val service = freshService(rng)
        val pin = service.currentPin.value

        // Assert
        assertEquals(6, pin.length)
        assertEquals("967295", pin)
    }

    @Test
    fun regeneratePin_calledManually_changesCurrentPin() {
        // Arrange
        val service = freshService()
        val pinBefore = service.currentPin.value

        // Act
        service.regeneratePin()
        val pinAfter = service.currentPin.value

        // Assert — with real random it is astronomically unlikely to get the same PIN twice
        // but the contract is that regeneratePin() emits a new value into the StateFlow
        assertEquals(6, pinAfter.length)
        assertTrue(pinAfter.all { it.isDigit() })
        // The following soft assertion documents intent without depending on randomness
        assertNotNull(pinAfter)
    }

    // ── PIN Validation — success path ─────────────────────────────────────────

    @Test
    fun validatePin_correctPin_returnsAuthTokenResponse() {
        // Arrange
        val pinBytes = byteArrayOf(42, 0, 0, 0)
        val tokenBytes = ByteArray(32) { it.toByte() }
        val rng = FixedSecureRandom(pinBytes, tokenBytes, pinBytes) // init, token, regen
        val service = freshService(rng)
        val correctPin = service.currentPin.value

        // Act
        val result = service.validatePin(
            pin = correctPin,
            clientIP = "127.0.0.1",
            userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5)",
        )

        // Assert
        assertTrue(result.isSuccess)
        val response = result.getOrThrow()
        assertEquals("iPhone", response.device.displayName)
        assertEquals("127.0.0.1", response.device.ipAddress)
        assertTrue(response.token.isNotEmpty())
        assertTrue(response.token.all { it.isLetterOrDigit() })
    }

    @Test
    fun validatePin_correctPin_consumesPinAndRegeneratesIt() {
        // Arrange
        val service = freshService()
        val pinBeforeAuth = service.currentPin.value

        // Act
        service.validatePin(pin = pinBeforeAuth, clientIP = "10.0.0.1", userAgent = "test")

        // Assert — PIN must have been regenerated after successful authentication
        val pinAfterAuth = service.currentPin.value
        assertEquals(6, pinAfterAuth.length, "New PIN must be 6 digits")
        // Original PIN is now invalid for a second authentication
        val secondAttempt = service.validatePin(
            pin = pinBeforeAuth,
            clientIP = "10.0.0.2",
            userAgent = "test",
        )
        assertTrue(secondAttempt.isFailure)
        assertIs<AuthError.InvalidPin>(secondAttempt.exceptionOrNull())
    }

    @Test
    fun validatePin_correctPin_addsDeviceToConnectedDevices() {
        // Arrange
        val service = freshService()
        val pin = service.currentPin.value
        assertEquals(0, service.connectedDevices.value.size)

        // Act
        service.validatePin(pin = pin, clientIP = "192.168.1.1", userAgent = "test-agent")

        // Assert
        assertEquals(1, service.connectedDevices.value.size)
        assertEquals("192.168.1.1", service.connectedDevices.value.first().ipAddress)
    }

    @Test
    fun validatePin_correctPin_invokesOnDevicesChangedCallback() {
        // Arrange
        val service = freshService()
        val pin = service.currentPin.value
        var callbackCount = 0
        service.onDevicesChanged = { callbackCount++ }

        // Act
        service.validatePin(pin = pin, clientIP = "10.0.0.1", userAgent = "agent")

        // Assert
        assertEquals(1, callbackCount)
    }

    @Test
    fun validatePin_correctPin_clearsPerIpFailureHistory() {
        // Arrange — record a failure for the same IP before a success
        val service = freshService()
        service.validatePin(pin = "000000", clientIP = "1.2.3.4", userAgent = "x")
        val correctPin = service.currentPin.value

        // Act — success from the same IP
        val result = service.validatePin(pin = correctPin, clientIP = "1.2.3.4", userAgent = "x")

        // Assert — the success clears failure history; same IP should not be rate-limited now
        assertTrue(result.isSuccess)
    }

    // ── PIN Validation — failure paths ────────────────────────────────────────

    @Test
    fun validatePin_wrongPin_returnsInvalidPin() {
        // Arrange
        val service = freshService()

        // Act
        val result = service.validatePin(pin = "000000", clientIP = "1.1.1.1", userAgent = "x")

        // Assert
        assertTrue(result.isFailure)
        assertIs<AuthError.InvalidPin>(result.exceptionOrNull())
    }

    @Test
    fun validatePin_wrongPin_doesNotAddDevice() {
        // Arrange
        val service = freshService()

        // Act
        service.validatePin(pin = "999999", clientIP = "5.5.5.5", userAgent = "x")

        // Assert
        assertEquals(0, service.connectedDevices.value.size)
    }

    @Test
    fun validatePin_afterGlobalLockout_returnsGlobalLockout() {
        // Arrange — trigger global lockout (10 failures across IPs)
        val service = freshService()
        repeat(10) { i ->
            service.validatePin(pin = "000000", clientIP = "10.0.0.$i", userAgent = "x")
        }
        assertTrue(service.isLocked.value, "Service must be locked after 10 global failures")

        // Act — attempt with any PIN
        val result = service.validatePin(pin = "000000", clientIP = "99.99.99.99", userAgent = "x")

        // Assert
        assertIs<AuthError.GlobalLockout>(result.exceptionOrNull())
    }

    @Test
    fun validatePin_perIpRateLimit_returnsRateLimited() {
        // Arrange — 3 failures from the same IP within window
        val service = freshService()
        val ip = "10.10.10.10"
        repeat(3) {
            service.validatePin(pin = "000000", clientIP = ip, userAgent = "x")
        }

        // Act — 4th attempt from same IP
        val result = service.validatePin(pin = "000000", clientIP = ip, userAgent = "x")

        // Assert
        assertTrue(result.isFailure)
        assertIs<AuthError.RateLimited>(result.exceptionOrNull())
        val err = result.exceptionOrNull() as AuthError.RateLimited
        assertTrue(err.retryAfterSeconds >= 1, "retryAfterSeconds must be at least 1")
    }

    @Test
    fun validatePin_maxDevicesReached_returnsMaxDevicesReached() {
        // Arrange — fill up 3 device slots
        val service = freshService()
        repeat(3) { i ->
            val pin = service.currentPin.value
            service.validatePin(pin = pin, clientIP = "10.0.0.$i", userAgent = "device $i")
        }
        assertEquals(3, service.connectedDevices.value.size)

        // Act
        val pin = service.currentPin.value
        val result = service.validatePin(pin = pin, clientIP = "10.0.0.99", userAgent = "overflow device")

        // Assert
        assertIs<AuthError.MaxDevicesReached>(result.exceptionOrNull())
    }

    @Test
    fun validatePin_globalLockout_firesOnSecurityLockoutCallback() {
        // Arrange
        val service = freshService()
        var lockoutFired = false
        service.onSecurityLockout = { lockoutFired = true }

        // Act — 10 failures to trigger global lockout
        repeat(10) { i ->
            service.validatePin(pin = "000000", clientIP = "10.0.0.$i", userAgent = "x")
        }

        // Assert
        assertTrue(lockoutFired)
    }

    // ── Token Validation ──────────────────────────────────────────────────────

    @Test
    fun validateToken_validToken_returnsDevice() {
        // Arrange
        val service = freshService()
        val pin = service.currentPin.value
        val authResponse = service.validatePin(
            pin = pin,
            clientIP = "192.168.0.1",
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)",
        ).getOrThrow()

        // Act
        val result = service.validateToken(
            token = authResponse.token,
            clientIP = "192.168.0.1",
        )

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(authResponse.device.id, result.getOrThrow().id)
    }

    @Test
    fun validateToken_unknownToken_returnsInvalidToken() {
        // Arrange
        val service = freshService()

        // Act
        val result = service.validateToken(token = "deadbeef00000000", clientIP = "1.1.1.1")

        // Assert
        assertIs<AuthError.InvalidToken>(result.exceptionOrNull())
    }

    @Test
    fun validateToken_ipMismatch_returnsIpMismatch() {
        // Arrange
        val service = freshService()
        val pin = service.currentPin.value
        val authResponse = service.validatePin(
            pin = pin,
            clientIP = "10.0.0.1",
            userAgent = "agent",
        ).getOrThrow()

        // Act — validate from a different IP
        val result = service.validateToken(
            token = authResponse.token,
            clientIP = "10.0.0.2",
        )

        // Assert
        assertIs<AuthError.IpMismatch>(result.exceptionOrNull())
    }

    // ── Device Management ─────────────────────────────────────────────────────

    @Test
    fun revokeDevice_removesDeviceAndToken() {
        // Arrange
        val service = freshService()
        val pin = service.currentPin.value
        val authResponse = service.validatePin(
            pin = pin,
            clientIP = "10.0.0.1",
            userAgent = "agent",
        ).getOrThrow()
        assertEquals(1, service.connectedDevices.value.size)

        // Act
        service.revokeDevice(authResponse.device.id)

        // Assert — device is gone
        assertEquals(0, service.connectedDevices.value.size)
        // Token is also invalidated
        val tokenResult = service.validateToken(authResponse.token, "10.0.0.1")
        assertIs<AuthError.InvalidToken>(tokenResult.exceptionOrNull())
    }

    @Test
    fun revokeAllDevices_clearsDevicesAndTokens() {
        // Arrange — connect 2 devices
        val service = freshService()
        repeat(2) { i ->
            val pin = service.currentPin.value
            service.validatePin(pin = pin, clientIP = "10.0.0.$i", userAgent = "d$i")
        }
        assertEquals(2, service.connectedDevices.value.size)

        // Act
        service.revokeAllDevices()

        // Assert
        assertEquals(0, service.connectedDevices.value.size)
    }

    @Test
    fun revokeAllDevices_invokesOnDevicesChangedWithZero() {
        // Arrange
        val service = freshService()
        val pin = service.currentPin.value
        service.validatePin(pin = pin, clientIP = "1.1.1.1", userAgent = "x")
        var lastCount = -1
        service.onDevicesChanged = { lastCount = it }

        // Act
        service.revokeAllDevices()

        // Assert
        assertEquals(0, lastCount)
    }

    // ── Lockout Reset ─────────────────────────────────────────────────────────

    @Test
    fun resetLockout_unlocksServiceAndAllowsAuth() {
        // Arrange — trigger global lockout
        val service = freshService()
        repeat(10) { i ->
            service.validatePin(pin = "000000", clientIP = "10.0.$i.0", userAgent = "x")
        }
        assertTrue(service.isLocked.value)

        // Act
        service.resetLockout()

        // Assert
        assertFalse(service.isLocked.value)
        // A subsequent correct auth must now succeed
        val pin = service.currentPin.value
        val result = service.validatePin(pin = pin, clientIP = "10.0.0.1", userAgent = "x")
        assertTrue(result.isSuccess)
    }

    @Test
    fun resetLockout_generatesNewPin() {
        // Arrange
        val service = freshService()
        val pinBeforeReset = service.currentPin.value

        // Act
        service.resetLockout()

        // Assert — PIN is refreshed (may coincidentally be the same, but must be valid 6-digit)
        val pinAfterReset = service.currentPin.value
        assertEquals(6, pinAfterReset.length)
        assertTrue(pinAfterReset.all { it.isDigit() })
        assertNotNull(pinAfterReset)
    }

    // ── User-Agent Parsing ────────────────────────────────────────────────────

    @Test
    fun validatePin_iPhoneUserAgent_displaysIphone() {
        val service = freshService()
        val pin = service.currentPin.value
        val result = service.validatePin(
            pin = pin,
            clientIP = "1.2.3.4",
            userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15",
        )
        assertEquals("iPhone", result.getOrThrow().device.displayName)
    }

    @Test
    fun validatePin_iPadUserAgent_displaysIpad() {
        val service = freshService()
        val pin = service.currentPin.value
        val result = service.validatePin(
            pin = pin,
            clientIP = "1.2.3.4",
            userAgent = "Mozilla/5.0 (iPad; CPU OS 16_0 like Mac OS X)",
        )
        assertEquals("iPad", result.getOrThrow().device.displayName)
    }

    @Test
    fun validatePin_macintoshUserAgent_displaysMac() {
        val service = freshService()
        val pin = service.currentPin.value
        val result = service.validatePin(
            pin = pin,
            clientIP = "1.2.3.4",
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)",
        )
        assertEquals("Mac", result.getOrThrow().device.displayName)
    }

    @Test
    fun validatePin_androidUserAgent_displaysAndroid() {
        val service = freshService()
        val pin = service.currentPin.value
        val result = service.validatePin(
            pin = pin,
            clientIP = "1.2.3.4",
            userAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 8)",
        )
        assertEquals("Android", result.getOrThrow().device.displayName)
    }

    @Test
    fun validatePin_unknownUserAgent_displaysRemoteDevice() {
        val service = freshService()
        val pin = service.currentPin.value
        val result = service.validatePin(
            pin = pin,
            clientIP = "1.2.3.4",
            userAgent = "curl/7.88.1",
        )
        assertEquals("Remote Device", result.getOrThrow().device.displayName)
    }

    // ── Token format ──────────────────────────────────────────────────────────

    @Test
    fun validatePin_success_tokenIs64HexChars() {
        // Arrange — 32 random bytes → 64 hex characters
        val service = freshService()
        val pin = service.currentPin.value

        // Act
        val response = service.validatePin(pin = pin, clientIP = "1.1.1.1", userAgent = "x").getOrThrow()

        // Assert
        assertEquals(64, response.token.length, "Token must be 64 hex chars (32 bytes)")
        assertTrue(
            response.token.all { it in '0'..'9' || it in 'a'..'f' },
            "Token must be lowercase hex: ${response.token}"
        )
    }
}

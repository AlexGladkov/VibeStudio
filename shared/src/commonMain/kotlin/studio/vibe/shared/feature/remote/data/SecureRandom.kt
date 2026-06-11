package studio.vibe.shared.feature.remote.data

import kotlin.random.Random

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

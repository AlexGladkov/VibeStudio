package studio.vibe.shared.security

import studio.vibe.shared.service.remote.SecureRandom
import java.security.SecureRandom as JSecureRandom

/**
 * JVM implementation of [SecureRandom] backed by [java.security.SecureRandom].
 *
 * Uses [JSecureRandom.getInstanceStrong] on JDK 8+ which selects the strongest
 * available algorithm (e.g. NativePRNGBlocking on Linux, Windows-PRNG on Windows).
 * Falls back to the default [JSecureRandom] constructor if `getInstanceStrong`
 * throws (e.g. restricted JRE environments), which still provides a CSPRNG.
 *
 * The instance is eagerly seeded at construction time and reused — safe for
 * concurrent calls per JCA contract.
 */
object JavaSecureRandom : SecureRandom {

    private val rng: JSecureRandom = runCatching {
        JSecureRandom.getInstanceStrong()
    }.getOrElse {
        JSecureRandom()
    }

    override fun nextBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        rng.nextBytes(bytes)
        return bytes
    }
}

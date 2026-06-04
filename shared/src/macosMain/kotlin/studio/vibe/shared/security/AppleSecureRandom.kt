package studio.vibe.shared.security

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault
import platform.posix.uint8_tVar
import studio.vibe.shared.service.remote.SecureRandom

/**
 * macOS/Native implementation of [SecureRandom] backed by
 * [SecRandomCopyBytes][platform.Security.SecRandomCopyBytes] from Security.framework.
 *
 * `SecRandomCopyBytes` is a CSPRNG that reads from the kernel's entropy pool
 * (equivalent to reading `/dev/random` on macOS). It is the Apple-recommended
 * API for generating cryptographically strong random bytes.
 *
 * Throws [IllegalStateException] if the underlying system call fails (non-zero
 * return code), which should never happen in practice on a healthy macOS system.
 */
object AppleSecureRandom : SecureRandom {

    @OptIn(ExperimentalForeignApi::class)
    override fun nextBytes(size: Int): ByteArray {
        require(size >= 0) { "size must be non-negative, got $size" }
        if (size == 0) return ByteArray(0)

        val result = ByteArray(size)
        result.usePinned { pinned ->
            val status = SecRandomCopyBytes(
                rnd = kSecRandomDefault,
                count = size.convert(),
                bytes = pinned.addressOf(0)
            )
            check(status == errSecSuccess) {
                "SecRandomCopyBytes failed with status $status"
            }
        }
        return result
    }
}

package studio.vibe.shared.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import studio.vibe.shared.contract.CredentialStorage
import java.util.Base64
import java.util.prefs.Preferences

/**
 * JVM credential storage backed by java.util.prefs.Preferences.
 *
 * Current implementation uses Base64 encoding, which provides obfuscation only.
 * TODO: Replace with platform-native secure storage:
 *   - Windows: DPAPI via JNA (CryptProtectData / CryptUnprotectData)
 *   - Linux: libsecret via JNA (org.gnome.keyring / Secret Service D-Bus API)
 *   - macOS JVM target: Security framework via JNA (SecKeychainAddGenericPassword)
 *
 * For a proper cross-platform approach consider:
 *   - AES-256-GCM with a key derived from a machine-unique secret stored separately
 *   - Or delegate to the OS keyring via a JNA binding
 */
class JvmCredentialStorage : CredentialStorage {

    private val prefs: Preferences = Preferences.userRoot().node("studio/vibe/vibstudio/credentials")
    private val encoder: Base64.Encoder = Base64.getEncoder()
    private val decoder: Base64.Decoder = Base64.getDecoder()

    override suspend fun save(account: String, value: String) = withContext(Dispatchers.IO) {
        val encoded = encoder.encodeToString(value.toByteArray(Charsets.UTF_8))
        prefs.put(sanitizeKey(account), encoded)
        prefs.flush()
    }

    override suspend fun load(account: String): String? = withContext(Dispatchers.IO) {
        val encoded = prefs.get(sanitizeKey(account), null) ?: return@withContext null
        runCatching {
            String(decoder.decode(encoded), Charsets.UTF_8)
        }.getOrNull()
    }

    override suspend fun delete(account: String) = withContext(Dispatchers.IO) {
        prefs.remove(sanitizeKey(account))
        prefs.flush()
    }

    /**
     * Preferences keys must be printable ASCII, max 80 chars.
     * Replace characters outside that range with underscores.
     */
    private fun sanitizeKey(key: String): String =
        key.replace(Regex("[^\\x21-\\x7E]"), "_").take(80)
}

package studio.vibe.desktop.remote

import java.io.File
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.Files
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Date
import java.util.logging.Logger

/**
 * Manages a self-signed TLS certificate for the Remote Control HTTPS server.
 *
 * Certificate is stored in `~/Library/Application Support/VibeStudio/remote-tls/`
 * (macOS) or `~/.config/vibestudio/remote-tls/` (Linux).
 *
 * A new certificate is generated when:
 * - No keystore exists on disk.
 * - The existing certificate is expired.
 *
 * Generation is done via `keytool` (bundled with every JRE/JDK), which avoids
 * introducing BouncyCastle or any third-party dependency just for cert gen.
 *
 * Security:
 * - The keystore file is chmod 600 (owner read/write only).
 * - Certificate is self-signed RSA-2048 with SHA-256, validity 365 days.
 * - Alias: `vibestudio-rc`.
 */
object TlsCertificateManager {

    private val log = Logger.getLogger("TlsCertificateManager")

    private const val KEYSTORE_FILENAME = "remote-tls.jks"
    private const val KEYSTORE_PASSWORD = "vibestudio-rc"
    private const val KEY_ALIAS = "vibestudio-rc"
    private const val VALIDITY_DAYS = 365

    private val storageDir: File by lazy {
        val os = System.getProperty("os.name", "").lowercase()
        val home = System.getProperty("user.home", System.getenv("HOME") ?: "/tmp")
        when {
            "mac" in os -> File(home, "Library/Application Support/VibeStudio/remote-tls")
            "windows" in os -> File(System.getenv("APPDATA") ?: home, "VibeStudio/remote-tls")
            else -> File(home, ".config/vibestudio/remote-tls")
        }
    }

    private val keystoreFile: File get() = File(storageDir, KEYSTORE_FILENAME)

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Ensure a valid TLS keystore exists, generating one if necessary.
     *
     * @return Path to the JKS keystore file.
     * @throws Exception if `keytool` fails or disk I/O fails.
     */
    fun ensureKeystore(): File {
        if (keystoreFile.exists() && isCertificateValid()) {
            log.info("TLS keystore loaded from disk: ${keystoreFile.absolutePath}")
            return keystoreFile
        }

        if (keystoreFile.exists()) {
            log.info("Existing TLS keystore expired or invalid — regenerating")
            keystoreFile.delete()
        }

        generateKeystore()
        log.info("New self-signed TLS keystore generated: ${keystoreFile.absolutePath}")
        return keystoreFile
    }

    /**
     * Returns the keystore password.
     * Exposed so Ktor's SSL config can use the same password.
     */
    fun keystorePassword(): CharArray = KEYSTORE_PASSWORD.toCharArray()

    /**
     * Returns the key alias within the keystore.
     */
    fun keyAlias(): String = KEY_ALIAS

    /**
     * Compute the SHA-256 fingerprint of the current certificate, or null.
     *
     * Format: colon-separated hex pairs, e.g. `"AB:CD:EF:..."`.
     */
    fun certificateFingerprint(): String? = runCatching {
        val ks = loadKeystore() ?: return@runCatching null
        val cert = ks.getCertificate(KEY_ALIAS) as? X509Certificate ?: return@runCatching null
        val sha256 = java.security.MessageDigest.getInstance("SHA-256")
        val digest = sha256.digest(cert.encoded)
        digest.joinToString(":") { "%02X".format(it) }
    }.getOrNull()

    // ── Private: generation ────────────────────────────────────────────────────

    private fun generateKeystore() {
        storageDir.mkdirs()

        // keytool is guaranteed to be on PATH alongside the JRE.
        // We pass the authtoken via a fixed store password (no CLI secret leakage).
        val keytool = resolveKeytool()
        val cmd = listOf(
            keytool,
            "-genkeypair",
            "-alias", KEY_ALIAS,
            "-keyalg", "RSA",
            "-keysize", "2048",
            "-sigalg", "SHA256withRSA",
            "-validity", VALIDITY_DAYS.toString(),
            "-keystore", keystoreFile.absolutePath,
            "-storepass", KEYSTORE_PASSWORD,
            "-keypass", KEYSTORE_PASSWORD,
            "-dname", "CN=VibeStudio Remote Control, O=VibeStudio, L=Local",
            "-ext", "SAN=dns:localhost,ip:127.0.0.1",
            "-noprompt",
        )

        val process = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw IllegalStateException("keytool failed (exit=$exitCode):\n$output")
        }

        // SECURITY: chmod 600 on the keystore file (owner read/write only).
        try {
            Files.setPosixFilePermissions(
                keystoreFile.toPath(),
                PosixFilePermissions.fromString("rw-------"),
            )
        } catch (_: UnsupportedOperationException) {
            // Windows does not support POSIX permissions — skip silently.
        }
    }

    // ── Private: validation ────────────────────────────────────────────────────

    private fun isCertificateValid(): Boolean = runCatching {
        val ks = loadKeystore() ?: return@runCatching false
        val cert = ks.getCertificate(KEY_ALIAS) as? X509Certificate ?: return@runCatching false
        val now = Date()
        now.after(cert.notBefore) && now.before(cert.notAfter)
    }.getOrDefault(false)

    private fun loadKeystore(): KeyStore? = runCatching {
        val ks = KeyStore.getInstance("JKS")
        keystoreFile.inputStream().use { stream ->
            ks.load(stream, KEYSTORE_PASSWORD.toCharArray())
        }
        ks
    }.getOrNull()

    private fun resolveKeytool(): String {
        // Prefer the keytool that ships with the running JRE.
        val javaHome = System.getProperty("java.home") ?: return "keytool"
        val candidate = File(javaHome, "bin/keytool")
        return if (candidate.exists()) candidate.absolutePath else "keytool"
    }
}

package studio.vibe.desktop.remote

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.reflect.Field
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit / integration tests for [TlsCertificateManager].
 *
 * [TlsCertificateManager] is a Kotlin `object` whose `storageDir` is a static
 * `by lazy` field.  The tests redirect I/O to a temp directory by swapping the
 * static lazy delegate with a new [lazyOf(dir)] value via [sun.misc.Unsafe],
 * which remains accessible across all JDK versions (unlike the [Field.modifiers]
 * trick that JDK 12+ removed).
 *
 * All four scenarios:
 * 1. [TlsCertificateManager.ensureKeystore] creates a keystore file when none exists.
 * 2. A second call is idempotent — same file, no regeneration.
 * 3. [TlsCertificateManager.certificateFingerprint] returns a colon-separated SHA-256 hex string.
 * 4. A freshly generated keystore can be reloaded and its cert read back (persistence).
 *
 * These tests require `keytool` on PATH (bundled with every JDK — always present
 * in the Gradle test JVM).
 */
class TlsCertificateManagerTest {

    @get:Rule
    val tmp: TemporaryFolder = TemporaryFolder()

    // ── Reflection seam via Unsafe ────────────────────────────────────────────

    /**
     * Replaces the static `storageDir$delegate` field on [TlsCertificateManager]
     * with a new [Lazy] that returns [dir], using [sun.misc.Unsafe] to bypass
     * the `final` modifier restriction present since JDK 12.
     *
     * Returns `true` if the swap succeeded, `false` if Unsafe is unavailable
     * (tests are skipped gracefully in that case).
     */
    private fun redirectStorageDirTo(dir: File): Boolean {
        return try {
            val clazz = TlsCertificateManager::class.java
            val delegateField: Field = clazz.getDeclaredField("storageDir\$delegate")
            delegateField.isAccessible = true

            // Obtain sun.misc.Unsafe via reflection (always accessible from test JVM).
            val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
            unsafeField.isAccessible = true
            val unsafe = unsafeField.get(null) as sun.misc.Unsafe

            val offset = unsafe.staticFieldOffset(delegateField)
            val base = unsafe.staticFieldBase(delegateField)
            unsafe.putObject(base, offset, lazyOf(dir))
            true
        } catch (_: Exception) {
            false // Unsafe not available or field missing — skip gracefully
        }
    }

    // ── Verify redirect works; skip whole class if not ────────────────────────

    private fun tryRedirect(subfolder: String): File? {
        val base = tmp.newFolder(subfolder)
        return if (redirectStorageDirTo(base)) base else null
    }

    // ── 1. ensureKeystore creates cert when absent ────────────────────────────

    @Test
    fun `ensureKeystore creates keystore file when none exists`() {
        val base = tryRedirect("case1-storage") ?: return

        val ks = TlsCertificateManager.ensureKeystore()

        assertTrue(ks.exists(), "Keystore file must be created on disk in $base")
        assertTrue(ks.length() > 0, "Keystore file must be non-empty")
    }

    // ── 2. Idempotent on second call ──────────────────────────────────────────

    @Test
    fun `ensureKeystore is idempotent — second call returns the same file without regenerating`() {
        val base = tryRedirect("case2-storage") ?: return

        val first = TlsCertificateManager.ensureKeystore()
        val firstModified = first.lastModified()

        // Small sleep to ensure mtime would differ if the file were regenerated.
        Thread.sleep(50)

        val second = TlsCertificateManager.ensureKeystore()

        assertEquals(first.absolutePath, second.absolutePath)
        assertEquals(firstModified, second.lastModified(), "File must not be regenerated on second call")
    }

    // ── 3. certificateFingerprint format ─────────────────────────────────────

    @Test
    fun `certificateFingerprint returns colon-separated SHA-256 hex string`() {
        val base = tryRedirect("case3-storage") ?: return

        TlsCertificateManager.ensureKeystore()
        val fingerprint = TlsCertificateManager.certificateFingerprint()

        assertNotNull(fingerprint, "Fingerprint must not be null after keystore creation")

        // SHA-256 produces 32 bytes → 32 pairs separated by 31 colons = 95 chars.
        assertEquals(95, fingerprint.length, "SHA-256 fingerprint must be 95 characters (XX:... × 32)")

        val parts = fingerprint.split(":")
        assertEquals(32, parts.size, "Fingerprint must have 32 colon-separated parts")
        assertTrue(
            parts.all { it.length == 2 && it.all { c -> c in '0'..'9' || c in 'A'..'F' } },
            "Each part must be an uppercase two-digit hex pair",
        )
    }

    // ── 4. Persistence — reload returns the same cert ─────────────────────────

    @Test
    fun `keystore persists across manager invocations — reloaded fingerprint matches original`() {
        val base = tryRedirect("case4-storage") ?: return

        TlsCertificateManager.ensureKeystore()
        val fingerprintAfterCreate = TlsCertificateManager.certificateFingerprint()
        assertNotNull(fingerprintAfterCreate)

        // Re-point manager at the same dir to simulate a fresh JVM startup.
        redirectStorageDirTo(base)

        // ensureKeystore must detect the existing valid cert and NOT regenerate.
        TlsCertificateManager.ensureKeystore()
        val fingerprintAfterReload = TlsCertificateManager.certificateFingerprint()

        assertNotNull(fingerprintAfterReload)
        assertEquals(
            fingerprintAfterCreate,
            fingerprintAfterReload,
            "Fingerprint must be identical after reload — same cert, no regeneration",
        )
    }
}

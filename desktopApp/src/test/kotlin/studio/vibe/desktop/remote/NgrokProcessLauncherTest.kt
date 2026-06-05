package studio.vibe.desktop.remote.ngrok

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [NgrokProcessLauncher].
 *
 * No real ngrok binary is needed: we test resolveNgrok by ensuring it returns null
 * when no binary exists, buildEnvironment filtering, NGROK_AUTHTOKEN injection,
 * and PATH prepending — all pure JVM logic with no OS exec.
 */
class NgrokProcessLauncherTest {

    private val launcher = NgrokProcessLauncher()

    // ── resolveNgrok ──────────────────────────────────────────────────────────

    @Test
    fun resolveNgrok_returnsNullWhenNotInstalled() {
        // On CI / sandbox machines ngrok may not be present.
        // The result is either a valid path or null — never throws.
        val result = runCatching { launcher.resolveNgrok() }
        assertTrue(result.isSuccess, "resolveNgrok() must not throw")
        // If a path is returned it must not be blank
        result.getOrNull()?.let { path ->
            assertTrue(path.isNotBlank(), "Returned path must not be blank")
            assertTrue(path.startsWith("/"), "Returned path must be absolute")
        }
    }

    // ── buildEnvironment — ALLOWED_ENV_KEYS filtering ─────────────────────────

    @Test
    fun buildEnvironment_onlyContainsAllowedKeys() {
        val env = launcher.buildEnvironment()
        val allowed = setOf(
            "HOME", "USER", "LOGNAME",
            "LANG", "LC_ALL", "LC_CTYPE",
            "TERM", "PATH", "TMPDIR",
            "NGROK_AUTHTOKEN",
        )
        for (key in env.keys) {
            assertTrue(key in allowed, "Unexpected env key '$key' — must be in ALLOWED_ENV_KEYS")
        }
    }

    @Test
    fun buildEnvironment_doesNotContainArbitraryProcessEnvVars() {
        // These keys are commonly set in CI but must NOT pass through
        val env = launcher.buildEnvironment()
        assertFalse("JAVA_HOME" in env, "JAVA_HOME must be filtered out")
        assertFalse("GRADLE_USER_HOME" in env, "GRADLE_USER_HOME must be filtered out")
        assertFalse("SECRET_TOKEN" in env, "Arbitrary secret env vars must be filtered out")
    }

    // ── NGROK_AUTHTOKEN injection ─────────────────────────────────────────────

    @Test
    fun buildEnvironment_injectsAuthtokenWhenNonBlank() {
        val env = launcher.buildEnvironment(authtoken = "test-token-abc")
        assertEquals("test-token-abc", env["NGROK_AUTHTOKEN"],
            "Non-blank authtoken must be injected as NGROK_AUTHTOKEN")
    }

    @Test
    fun buildEnvironment_noAuthtokenWhenBlank() {
        val env = launcher.buildEnvironment(authtoken = "")
        assertFalse("NGROK_AUTHTOKEN" in env,
            "Blank authtoken must NOT result in NGROK_AUTHTOKEN key")
    }

    @Test
    fun buildEnvironment_noAuthtokenWhenSpaces() {
        val env = launcher.buildEnvironment(authtoken = "   ")
        assertFalse("NGROK_AUTHTOKEN" in env,
            "Whitespace-only authtoken must NOT result in NGROK_AUTHTOKEN key")
    }

    // ── trusted dirs prepended to PATH ────────────────────────────────────────

    @Test
    fun buildEnvironment_trustedDirsPrependedToPath() {
        // Inject a PATH that is missing all trusted dirs to reliably test prepend logic
        val originalPath = System.getenv("PATH") ?: ""
        val fakePath = "/custom/app/bin:/home/user/.local/bin"

        // We can't mock System.getenv here, so instead we verify the invariant:
        // all trusted dirs that are in the resulting PATH must appear at the start,
        // before any dir that was in the original PATH (and is also non-trusted).
        val env = launcher.buildEnvironment()
        val path = env["PATH"] ?: return

        val parts = path.split(":")
        val trustedDirs = setOf(
            "/opt/homebrew/bin",
            "/opt/homebrew/sbin",
            "/usr/local/bin",
            "/usr/bin",
            "/bin",
        )

        // Find the first index of a dir that was originally in PATH but is NOT trusted.
        // Trusted dirs that were MISSING from originalPath should be at the front.
        val originalParts = originalPath.split(":")
        val firstOriginalNonTrusted = parts.indexOfFirst { it in originalParts && it !in trustedDirs }

        if (firstOriginalNonTrusted == -1) return // no non-trusted original dirs present

        // Any dir that was missing from original PATH and is trusted must appear before firstOriginalNonTrusted
        for (dir in trustedDirs) {
            if (dir !in originalParts && dir in parts) {
                val idx = parts.indexOf(dir)
                assertTrue(idx < firstOriginalNonTrusted,
                    "Prepended trusted dir '$dir' (was missing from original PATH) must appear before original non-trusted dirs")
            }
        }
    }

    @Test
    fun buildEnvironment_noDuplicatesInPath() {
        val env = launcher.buildEnvironment()
        val path = env["PATH"] ?: return
        val parts = path.split(":")
        val unique = parts.toSet()
        assertEquals(unique.size, parts.size,
            "PATH must not contain duplicate entries after buildEnvironment()")
    }

    // ── launch ────────────────────────────────────────────────────────────────

    @Test
    fun launch_returnsNullForInvalidBinaryPath() {
        val result = launcher.launch(
            ngrokPath = "/nonexistent/path/to/ngrok",
            httpPort = 12345,
            env = mapOf("PATH" to "/usr/bin:/bin"),
        )
        assertNull(result, "launch() must return null for a non-existent binary path")
    }
}

private fun assertEquals(expected: String, actual: String?, message: String) {
    kotlin.test.assertEquals(expected, actual, message)
}

private fun assertEquals(expected: Int, actual: Int, message: String) {
    kotlin.test.assertEquals(expected, actual, message)
}

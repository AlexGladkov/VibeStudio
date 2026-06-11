package studio.vibe.shared.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import studio.vibe.shared.core.common.CredentialStorage

private const val ACCOUNT_NAME = "VibeStudio"

/**
 * JVM credential storage backed by the macOS Keychain via the `security` CLI.
 *
 * Credentials are stored as generic passwords with:
 *   - account  = [ACCOUNT_NAME] ("VibeStudio")
 *   - service  = the caller-supplied [account] key
 *   - password = the plaintext [value]
 *
 * The `-U` flag on `add-generic-password` makes the call idempotent: it updates
 * an existing item rather than failing with "already exists".
 *
 * All subprocess calls run on [Dispatchers.IO]. Errors during [load] and [delete]
 * are swallowed gracefully — [load] returns `null`, [delete] is a no-op on failure.
 */
class JvmCredentialStorage : CredentialStorage {

    override suspend fun save(account: String, value: String): Unit = withContext(Dispatchers.IO) {
        ProcessBuilder(
            "security", "add-generic-password",
            "-a", ACCOUNT_NAME,
            "-s", account,
            "-w", value,
            "-U",
        )
            .redirectErrorStream(true)
            .start()
            .waitFor()
    }

    override suspend fun load(account: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val process = ProcessBuilder(
                "security", "find-generic-password",
                "-a", ACCOUNT_NAME,
                "-s", account,
                "-w",
            )
                .redirectErrorStream(false)
                .start()

            val exitCode = process.waitFor()
            if (exitCode != 0) return@runCatching null

            process.inputStream
                .bufferedReader(Charsets.UTF_8)
                .readText()
                .trimEnd('\n')
        }.getOrNull()
    }

    override suspend fun delete(account: String): Unit = withContext(Dispatchers.IO) {
        runCatching {
            ProcessBuilder(
                "security", "delete-generic-password",
                "-a", ACCOUNT_NAME,
                "-s", account,
            )
                .redirectErrorStream(true)
                .start()
                .waitFor()
        }
        Unit
    }
}

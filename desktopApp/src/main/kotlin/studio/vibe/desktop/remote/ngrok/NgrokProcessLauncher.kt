package studio.vibe.desktop.remote.ngrok

import java.io.File
import java.util.logging.Logger

/**
 * Resolves the ngrok binary and builds the OS process.
 *
 * All environment filtering happens here: only [ALLOWED_ENV_KEYS] pass through,
 * and the authtoken is injected via env var (not CLI args) to stay out of `ps aux`.
 */
internal class NgrokProcessLauncher {

    companion object {
        private val log = Logger.getLogger("NgrokProcessLauncher")

        private val TRUSTED_DIRS = listOf(
            "/opt/homebrew/bin",
            "/opt/homebrew/sbin",
            "/usr/local/bin",
            "/usr/bin",
            "/bin",
        )

        private val ALLOWED_ENV_KEYS = setOf(
            "HOME", "USER", "LOGNAME",
            "LANG", "LC_ALL", "LC_CTYPE",
            "TERM", "PATH", "TMPDIR",
            "NGROK_AUTHTOKEN",
        )
    }

    /**
     * Resolve the ngrok executable from trusted directories then PATH.
     *
     * @return Absolute path to the ngrok binary, or null if not found.
     */
    fun resolveNgrok(): String? {
        for (dir in TRUSTED_DIRS) {
            val f = File(dir, "ngrok")
            if (f.canExecute()) return f.absolutePath
        }
        val pathDirs = System.getenv("PATH")?.split(":") ?: emptyList()
        for (dir in pathDirs) {
            val f = File(dir, "ngrok")
            if (f.canExecute()) return f.absolutePath
        }
        return null
    }

    /**
     * Build a filtered environment map from the current process environment.
     *
     * Injects trusted directories into PATH if they are missing.
     *
     * @param authtoken Optional ngrok authtoken; injected as NGROK_AUTHTOKEN if non-blank.
     */
    fun buildEnvironment(authtoken: String = ""): MutableMap<String, String> {
        val result = mutableMapOf<String, String>()
        val processEnv = System.getenv()
        for (key in ALLOWED_ENV_KEYS) {
            processEnv[key]?.let { result[key] = it }
        }

        // Ensure trusted dirs are in PATH so ngrok can find its dependencies.
        val currentPath = result["PATH"] ?: "/usr/bin:/bin:/usr/sbin:/sbin"
        val existingParts = currentPath.split(":")
        val missing = TRUSTED_DIRS.filter { it !in existingParts }
        if (missing.isNotEmpty()) {
            result["PATH"] = (missing + existingParts).joinToString(":")
        }

        if (authtoken.isNotBlank()) {
            result["NGROK_AUTHTOKEN"] = authtoken
        }

        return result
    }

    /**
     * Launch the ngrok subprocess for [httpPort].
     *
     * @param ngrokPath Absolute path to the ngrok binary.
     * @param httpPort  Local port to expose.
     * @param env       Pre-built environment map (from [buildEnvironment]).
     * @return The started [Process], or null on launch failure (error logged internally).
     */
    fun launch(ngrokPath: String, httpPort: Int, env: Map<String, String>): Process? {
        val cmd = listOf(
            ngrokPath,
            "http",
            "https://localhost:$httpPort",
            "--verify-upstream-tls=false",
        )

        val pb = ProcessBuilder(cmd).apply {
            environment().clear()
            environment().putAll(env)
            redirectErrorStream(false)
        }

        return runCatching { pb.start() }.getOrElse { e ->
            log.severe("NgrokProcessLauncher: failed to launch: ${e.message}")
            null
        }
    }
}

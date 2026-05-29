package studio.vibe.desktop.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.logging.Logger

/**
 * Manages an ngrok tunnel subprocess to expose the local Remote Control HTTP
 * server to the internet.
 *
 * Matches the behavior of Swift `NgrokTunnelService` exactly:
 * - Resolves the `ngrok` binary from trusted directories.
 * - Launches `ngrok http https://localhost:<port> --verify-upstream-tls=false`.
 * - Passes the authtoken via env var (not CLI args) to keep it out of `ps aux`.
 * - Tracks our own PID in `/tmp/vibestudio-ngrok.pid` and kills it on next start.
 * - Polls `http://localhost:4040/api/tunnels` for the public URL.
 * - Sends SIGTERM on stop, escalates to SIGKILL after 5 seconds.
 *
 * All state mutations happen on the coroutine scope's dispatcher.
 * Thread-safe: only [_isRunning], [_tunnelURL], [_error] are accessed from
 * the calling scope; the process handle is confined to [scope].
 */
class NgrokTunnelService(private val scope: CoroutineScope) {

    companion object {
        private val log = Logger.getLogger("NgrokTunnelService")
        private val PID_FILE = File("/tmp/vibestudio-ngrok.pid")

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

    // ── Observable state ──────────────────────────────────────────────────────

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _tunnelURL = MutableStateFlow<String?>(null)
    val tunnelURL: StateFlow<String?> = _tunnelURL.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ── Private process state (confined to scope) ──────────────────────────────

    @Volatile private var process: Process? = null
    @Volatile private var pollJob: Job? = null

    // ── Start ──────────────────────────────────────────────────────────────────

    /**
     * Start the ngrok tunnel for [httpPort].
     *
     * @param httpPort  Local port to tunnel (e.g. 7842).
     * @param authtoken ngrok authtoken. Pass empty string to use system config.
     */
    fun start(httpPort: Int, authtoken: String = "") {
        if (_isRunning.value) return

        _error.value = null
        _tunnelURL.value = null

        val ngrokPath = resolveNgrok() ?: run {
            _error.value = "ngrok not found. Install: brew install ngrok"
            log.warning("NgrokTunnelService: ngrok binary not found in trusted directories")
            return
        }

        scope.launch(Dispatchers.IO) {
            // Kill any stale ngrok process from a previous VibeStudio session.
            killPreviousOwnedProcess()
            delay(500)

            // Build minimal environment — authtoken via env var, not CLI.
            val env = buildEnvironment()
            if (authtoken.isNotBlank()) {
                env["NGROK_AUTHTOKEN"] = authtoken
            }

            launchNgrok(ngrokPath, httpPort, env)
        }
    }

    // ── Stop ───────────────────────────────────────────────────────────────────

    /**
     * Stop the ngrok tunnel and terminate the subprocess.
     *
     * Sends SIGTERM first, then SIGKILL after 5 seconds if still running.
     */
    fun stop() {
        pollJob?.cancel()
        pollJob = null

        val proc = process
        if (proc != null && proc.isAlive) {
            proc.destroy()  // SIGTERM
            scope.launch(Dispatchers.IO) {
                delay(5_000)
                if (proc.isAlive) {
                    proc.destroyForcibly()  // SIGKILL
                    log.warning("NgrokTunnelService: escalated to SIGKILL")
                }
            }
        }

        removePidFile()
        process = null
        _isRunning.value = false
        _tunnelURL.value = null
        _error.value = null
        log.info("NgrokTunnelService: stopped")
    }

    // ── Private: launch ────────────────────────────────────────────────────────

    private fun launchNgrok(path: String, httpPort: Int, env: MutableMap<String, String>) {
        val cmd = listOf(
            path,
            "http",
            "https://localhost:$httpPort",
            "--verify-upstream-tls=false",
        )

        val pb = ProcessBuilder(cmd).apply {
            environment().clear()
            environment().putAll(env)
            redirectErrorStream(false)
        }

        val proc = runCatching { pb.start() }.getOrElse { e ->
            _error.value = "Failed to launch ngrok: ${e.message}"
            log.severe("NgrokTunnelService: failed to launch: ${e.message}")
            return
        }

        writePidFile(proc.pid())
        process = proc
        _isRunning.value = true
        log.info("NgrokTunnelService: started, tunneling port $httpPort")

        // Watch for unexpected exit.
        scope.launch(Dispatchers.IO) {
            val exitCode = proc.waitFor()
            if (process === proc) {
                process = null
                _isRunning.value = false
                pollJob?.cancel()
                pollJob = null
                if (exitCode != 0 && _error.value == null) {
                    val stderr = runCatching {
                        proc.errorStream.bufferedReader().readText().trim()
                    }.getOrDefault("")
                    _error.value = if (stderr.isNotBlank()) "ngrok: $stderr"
                    else "ngrok exited with code $exitCode"
                }
                log.info("NgrokTunnelService: process exited code=$exitCode")
            }
        }

        // Poll for the public URL.
        startPollingForUrl()
    }

    // ── Private: URL polling ───────────────────────────────────────────────────

    private fun startPollingForUrl() {
        pollJob = scope.launch(Dispatchers.IO) {
            val maxAttempts = 15
            for (attempt in 1..maxAttempts) {
                if (!_isRunning.value) return@launch
                delay(2_000)
                if (!_isRunning.value) return@launch

                val url = runCatching {
                    fetchTunnelUrl("http://localhost:4040/api/tunnels")
                }.getOrNull()

                if (url != null) {
                    _tunnelURL.value = url
                    log.info("NgrokTunnelService: tunnel URL = $url")
                    return@launch
                }

                log.fine("NgrokTunnelService: poll attempt $attempt/$maxAttempts — not ready yet")
            }

            if (_isRunning.value && _tunnelURL.value == null) {
                _error.value = "Failed to obtain tunnel URL"
                log.warning("NgrokTunnelService: failed to obtain tunnel URL after $maxAttempts attempts")
            }
        }
    }

    private fun fetchTunnelUrl(apiUrl: String): String? {
        val conn = URL(apiUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 1_500
        conn.readTimeout = 1_500
        return try {
            val body = conn.inputStream.bufferedReader().readText()
            val root = JSONObject(body)
            val tunnels = root.optJSONArray("tunnels") ?: return null
            if (tunnels.length() == 0) return null
            val first = tunnels.getJSONObject(0)
            first.optString("public_url").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    // ── Private: binary resolution ─────────────────────────────────────────────

    private fun resolveNgrok(): String? {
        for (dir in TRUSTED_DIRS) {
            val f = File(dir, "ngrok")
            if (f.canExecute()) return f.absolutePath
        }
        // Also check the system PATH.
        val pathDirs = System.getenv("PATH")?.split(":") ?: emptyList()
        for (dir in pathDirs) {
            val f = File(dir, "ngrok")
            if (f.canExecute()) return f.absolutePath
        }
        return null
    }

    // ── Private: environment ───────────────────────────────────────────────────

    private fun buildEnvironment(): MutableMap<String, String> {
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

        return result
    }

    // ── Private: PID file ──────────────────────────────────────────────────────

    private fun writePidFile(pid: Long) {
        runCatching { PID_FILE.writeText("$pid\n") }
        log.fine("NgrokTunnelService: wrote PID file pid=$pid")
    }

    private fun removePidFile() {
        runCatching { PID_FILE.delete() }
    }

    private fun killPreviousOwnedProcess() {
        val content = runCatching { PID_FILE.readText().trim() }.getOrNull() ?: return
        val pid = content.toLongOrNull()?.takeIf { it > 1 } ?: return

        if (!processNameMatches(pid, "ngrok")) {
            log.fine("NgrokTunnelService: PID $pid from pid file is no longer ngrok — skipping kill")
            removePidFile()
            return
        }

        // SIGTERM
        withContext(scope) {
            runCatching {
                ProcessBuilder("kill", "-TERM", pid.toString()).start().waitFor()
            }
        }
        log.info("NgrokTunnelService: sent SIGTERM to previous owned ngrok pid=$pid")

        // Wait up to 1s for process to exit.
        var waited = 0
        while (waited < 10) {
            Thread.sleep(100)
            waited++
            if (!isProcessAlive(pid)) break
        }

        if (isProcessAlive(pid)) {
            runCatching {
                ProcessBuilder("kill", "-KILL", pid.toString()).start().waitFor()
            }
            log.warning("NgrokTunnelService: escalated to SIGKILL for previous owned ngrok pid=$pid")
        }

        removePidFile()
    }

    private fun processNameMatches(pid: Long, expectedName: String): Boolean = runCatching {
        val proc = ProcessBuilder("/bin/ps", "-p", pid.toString(), "-o", "comm=")
            .redirectErrorStream(true)
            .start()
        proc.waitFor()
        val output = proc.inputStream.bufferedReader().readText().trim()
        output == expectedName
    }.getOrDefault(false)

    private fun isProcessAlive(pid: Long): Boolean = runCatching {
        val proc = ProcessBuilder("kill", "-0", pid.toString())
            .redirectErrorStream(true)
            .start()
        proc.waitFor() == 0
    }.getOrDefault(false)

    // Convenience: run a blocking call in the IO dispatcher without suspend context.
    private fun <T> withContext(scope: CoroutineScope, block: () -> T) {
        // Fire-and-forget on IO dispatcher — used for synchronous process ops.
        try { block() } catch (_: Exception) {}
    }
}

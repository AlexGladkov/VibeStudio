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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    @Volatile private var stopRequested = false

    // Guards the start/stop prelude so the two operations are mutually exclusive.
    // Without this lock, stop() can set stopRequested=true between start()'s
    // _isRunning.value check and the stopRequested=false reset (TOCTOU).
    private val lifecycleMutex = Mutex()

    // ── Start ──────────────────────────────────────────────────────────────────

    /**
     * Start the ngrok tunnel for [httpPort].
     *
     * The _isRunning check and stopRequested reset are performed inside
     * [lifecycleMutex] so a concurrent [stop] cannot slip in between the two
     * writes (TOCTOU fix).
     *
     * @param httpPort  Local port to tunnel (e.g. 7842).
     * @param authtoken ngrok authtoken. Pass empty string to use system config.
     */
    fun start(httpPort: Int, authtoken: String = "") {
        scope.launch(Dispatchers.IO) {
            val ngrokPath = lifecycleMutex.withLock {
                // Re-check under lock: stop() might have run between the caller's
                // decision to call start() and our acquiring the lock.
                if (_isRunning.value) return@launch

                stopRequested = false
                _error.value = null
                _tunnelURL.value = null

                resolveNgrok() ?: run {
                    _error.value = "ngrok not found. Install: brew install ngrok"
                    log.warning("NgrokTunnelService: ngrok binary not found in trusted directories")
                    return@launch
                }
            }

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
     * The stopRequested flag and _isRunning reset are performed inside
     * [lifecycleMutex] so a concurrent [start] cannot slip in and clear
     * stopRequested after we set it (TOCTOU fix).
     *
     * Sends SIGTERM first, then SIGKILL after 5 seconds if still running.
     */
    fun stop() {
        scope.launch(Dispatchers.IO) {
            val proc = lifecycleMutex.withLock {
                // 1. Signal exit-watcher coroutine first — it gates on stopRequested.
                stopRequested = true
                // 2. Clear observable state so UI reacts immediately.
                _isRunning.value = false

                pollJob?.cancel()
                pollJob = null

                // 3. Capture and null out process under lock so start() cannot
                //    observe a partially-stopped state.
                val captured = process
                process = null
                captured
            }

            if (proc != null && proc.isAlive) {
                // 4. Send SIGTERM.
                proc.destroy()
                // 5. Escalation after SIGTERM.
                delay(5_000)
                if (proc.isAlive) {
                    proc.destroyForcibly()  // SIGKILL
                    log.warning("NgrokTunnelService: escalated to SIGKILL")
                }
            }

            removePidFile()
            _tunnelURL.value = null
            _error.value = null
            log.info("NgrokTunnelService: stopped")
        }
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
            if (!stopRequested) {
                // Unexpected exit (not triggered by stop()).
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
                log.info("NgrokTunnelService: process exited unexpectedly, code=$exitCode")
            } else {
                log.info("NgrokTunnelService: process exited after stop(), code=$exitCode")
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

    private suspend fun killPreviousOwnedProcess() {
        val content = runCatching { PID_FILE.readText().trim() }.getOrNull() ?: return
        val pid = content.toLongOrNull()?.takeIf { it > 1 } ?: return

        if (!processNameMatches(pid, "ngrok")) {
            log.fine("NgrokTunnelService: PID $pid from pid file is no longer ngrok — skipping kill")
            removePidFile()
            return
        }

        // SIGTERM
        runIgnoringExceptions {
            ProcessBuilder("kill", "-TERM", pid.toString()).start().waitFor()
        }
        log.info("NgrokTunnelService: sent SIGTERM to previous owned ngrok pid=$pid")

        // Wait up to 1s for process to exit.
        repeat(10) {
            delay(100)
            if (!isProcessAlive(pid)) return@repeat
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

    private inline fun runIgnoringExceptions(block: () -> Unit) {
        try { block() } catch (_: Exception) {}
    }
}

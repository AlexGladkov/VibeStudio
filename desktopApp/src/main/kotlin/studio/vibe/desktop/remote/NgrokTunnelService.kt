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
import studio.vibe.desktop.remote.ngrok.NgrokProcessLauncher
import studio.vibe.desktop.remote.ngrok.NgrokUrlPoller
import java.io.File
import java.util.logging.Logger

/**
 * Manages an ngrok tunnel subprocess to expose the local Remote Control HTTP
 * server to the internet.
 *
 * Lifecycle and state flow orchestration lives here. Subprocess construction is
 * delegated to [NgrokProcessLauncher]; URL discovery to [NgrokUrlPoller].
 *
 * Thread-safe: all state mutations happen under [lifecycleMutex] on [Dispatchers.IO].
 * The [_isRunning], [_tunnelURL], and [_error] flows are safe to observe from any thread.
 */
class NgrokTunnelService(private val scope: CoroutineScope) {

    companion object {
        private val log = Logger.getLogger("NgrokTunnelService")
        private val PID_FILE = File("/tmp/vibestudio-ngrok.pid")
    }

    // ── Observable state ──────────────────────────────────────────────────────

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _tunnelURL = MutableStateFlow<String?>(null)
    val tunnelURL: StateFlow<String?> = _tunnelURL.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ── Private helpers ────────────────────────────────────────────────────────

    private val launcher = NgrokProcessLauncher()
    private val urlPoller = NgrokUrlPoller()

    // ── Private process state (confined to scope) ──────────────────────────────

    @Volatile private var process: Process? = null
    @Volatile private var pollJob: Job? = null
    @Volatile private var stopRequested = false

    // Guards start/stop prelude so the two operations are mutually exclusive (TOCTOU fix).
    private val lifecycleMutex = Mutex()

    // ── Start ──────────────────────────────────────────────────────────────────

    /**
     * Start the ngrok tunnel for [httpPort].
     *
     * @param httpPort  Local port to tunnel (e.g. 7842).
     * @param authtoken ngrok authtoken. Pass empty string to use system config.
     */
    fun start(httpPort: Int, authtoken: String = "") {
        scope.launch(Dispatchers.IO) {
            val ngrokPath = lifecycleMutex.withLock {
                if (_isRunning.value) return@launch

                stopRequested = false
                _error.value = null
                _tunnelURL.value = null

                launcher.resolveNgrok() ?: run {
                    _error.value = "ngrok not found. Install: brew install ngrok"
                    log.warning("NgrokTunnelService: ngrok binary not found in trusted directories")
                    return@launch
                }
            }

            // Kill any stale ngrok process from a previous VibeStudio session.
            killPreviousOwnedProcess()
            delay(500)

            val env = launcher.buildEnvironment(authtoken)
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
        scope.launch(Dispatchers.IO) {
            val proc = lifecycleMutex.withLock {
                stopRequested = true
                _isRunning.value = false

                pollJob?.cancel()
                pollJob = null

                val captured = process
                process = null
                captured
            }

            if (proc != null && proc.isAlive) {
                proc.destroy()
                delay(5_000)
                if (proc.isAlive) {
                    proc.destroyForcibly()
                    log.warning("NgrokTunnelService: escalated to SIGKILL")
                }
            }

            removePidFile()
            _tunnelURL.value = null
            _error.value = null
            log.info("NgrokTunnelService: stopped")
        }
    }

    // ── Private: launch orchestration ─────────────────────────────────────────

    private suspend fun launchNgrok(path: String, httpPort: Int, env: MutableMap<String, String>) {
        // Spawn the OS process OUTSIDE the mutex — ProcessBuilder.start() can block.
        val proc = launcher.launch(path, httpPort, env) ?: run {
            _error.value = "Failed to launch ngrok"
            return
        }

        // Gate all state writes inside the mutex.
        val launched = lifecycleMutex.withLock {
            if (stopRequested) {
                proc.destroyForcibly()
                log.info("NgrokTunnelService: stop() raced launchNgrok — destroyed proc immediately")
                false
            } else {
                writePidFile(proc.pid())
                process = proc
                _isRunning.value = true
                log.info("NgrokTunnelService: started, tunneling port $httpPort")
                true
            }
        }
        if (!launched) return

        // Watch for unexpected exit.
        scope.launch(Dispatchers.IO) {
            val exitCode = proc.waitFor()
            lifecycleMutex.withLock {
                if (process !== proc && stopRequested) {
                    log.info("NgrokTunnelService: process exited after stop(), code=$exitCode")
                    return@withLock
                }
                if (!stopRequested) {
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
                    urlPoller.fetchTunnelUrl()
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

        runIgnoringExceptions {
            ProcessBuilder("kill", "-TERM", pid.toString()).start().waitFor()
        }
        log.info("NgrokTunnelService: sent SIGTERM to previous owned ngrok pid=$pid")

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

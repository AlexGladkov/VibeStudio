@file:OptIn(ExperimentalForeignApi::class)

package studio.vibe.shared.platform

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.posix.FILE
import platform.posix.SIGTERM
import platform.posix.fclose
import platform.posix.fread
import platform.posix.getpid
import platform.posix.kill
import platform.posix.pclose
import platform.posix.popen
import platform.posix.unlink
import studio.vibe.shared.contract.ProcessResult
import studio.vibe.shared.contract.ProcessRunner
import studio.vibe.shared.model.FilePath
import kotlin.concurrent.AtomicInt
import kotlin.time.Duration

/**
 * macOS [ProcessRunner] using POSIX popen/pclose.
 *
 * stderr is redirected to a temp file so stdout and stderr are captured separately.
 * The shell command is executed via `/bin/sh -c "..."` (implicit in popen).
 */
class MacosProcessRunner : ProcessRunner {

    private val counter = AtomicInt(0)

    override suspend fun run(
        command: List<String>,
        workDir: FilePath,
        env: Map<String, String>,
        timeout: Duration,
    ): ProcessResult = withContext(Dispatchers.Default) {
        val id = counter.addAndGet(1)
        val stderrTmp = "/tmp/kn_err_${getpid()}_$id"

        val envPrefix = env.entries.joinToString(" ") { (k, v) ->
            "${shellQuote(k)}=${shellQuote(v)}"
        }
        val cmdStr = command.joinToString(" ") { shellQuote(it) }
        val shell = buildString {
            append("cd ").append(shellQuote(workDir.path)).append(" && ")
            if (envPrefix.isNotEmpty()) append(envPrefix).append(' ')
            append(cmdStr).append(" 2>").append(shellQuote(stderrTmp))
        }

        val stdout = popenRead(shell)
        val stderr = readTextFile(stderrTmp)
        unlink(stderrTmp)

        ProcessResult(
            exitCode = stdout.second,
            stdout = stdout.first,
            stderr = stderr,
        )
    }

    override fun stream(
        command: List<String>,
        workDir: FilePath,
        env: Map<String, String>,
    ): Flow<String> = callbackFlow {
        val envPrefix = env.entries.joinToString(" ") { (k, v) ->
            "${shellQuote(k)}=${shellQuote(v)}"
        }
        val cmdStr = command.joinToString(" ") { shellQuote(it) }
        val shell = buildString {
            append("cd ").append(shellQuote(workDir.path)).append(" && ")
            if (envPrefix.isNotEmpty()) append(envPrefix).append(' ')
            append(cmdStr).append(" 2>&1")
        }

        val fp = popen(shell, "r")
        if (fp == null) {
            close()
            return@callbackFlow
        }

        val readJob = launch(Dispatchers.Default) {
            try {
                val buf = ByteArray(4096)
                while (isActive) {
                    val n = readChunk(fp, buf)
                    if (n <= 0) break
                    trySend(buf.decodeToString(0, n))
                }
            } finally {
                pclose(fp)
            }
        }

        awaitClose {
            readJob.cancel()
        }
    }

    // -- Helpers --

    /** Run shell command via popen, return (stdout, exitCode). */
    private fun popenRead(shell: String): Pair<String, Int> {
        val fp = popen(shell, "r") ?: return "" to 127
        val sb = StringBuilder()
        val buf = ByteArray(8192)
        while (true) {
            val n = readChunk(fp, buf)
            if (n <= 0) break
            sb.append(buf.decodeToString(0, n))
        }
        val status = pclose(fp)
        val exitCode = (status and 0xFF00) shr 8
        return sb.toString() to exitCode
    }

    private fun readChunk(fp: CPointer<FILE>, buf: ByteArray): Int {
        return buf.usePinned { pinned ->
            fread(pinned.addressOf(0), 1u, buf.size.toULong(), fp).toInt()
        }
    }

    /** Read entire text file; returns empty string if file does not exist. */
    private fun readTextFile(path: String): String {
        val fp = platform.posix.fopen(path, "r") ?: return ""
        val sb = StringBuilder()
        val buf = ByteArray(4096)
        while (true) {
            val n = buf.usePinned { pinned ->
                fread(pinned.addressOf(0), 1u, buf.size.toULong(), fp).toInt()
            }
            if (n <= 0) break
            sb.append(buf.decodeToString(0, n))
        }
        fclose(fp)
        return sb.toString()
    }

    private fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}

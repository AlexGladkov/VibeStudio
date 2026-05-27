package studio.vibe.shared.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.vibe.shared.contract.ProcessResult
import studio.vibe.shared.contract.ProcessRunner
import studio.vibe.shared.model.FilePath
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

class JvmProcessRunner : ProcessRunner {

    override suspend fun run(
        command: List<String>,
        workDir: FilePath,
        env: Map<String, String>,
        timeout: Duration,
    ): ProcessResult = withContext(Dispatchers.IO) {
        val builder = ProcessBuilder(command)
            .directory(File(workDir.path))
            .redirectErrorStream(false)

        builder.environment().clear()
        builder.environment().putAll(env)

        val process = builder.start()

        // Read stdout and stderr in parallel to avoid pipe deadlock
        val stdoutBuilder = StringBuilder()
        val stderrBuilder = StringBuilder()

        val stdoutJob = launch {
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    stdoutBuilder.appendLine(line)
                    line = reader.readLine()
                }
            }
        }

        val stderrJob = launch {
            BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    stderrBuilder.appendLine(line)
                    line = reader.readLine()
                }
            }
        }

        val exitCode = if (timeout == Duration.INFINITE) {
            process.waitFor()
        } else {
            val timedOut = !process.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            if (timedOut) {
                process.destroyForcibly()
                -1
            } else {
                process.exitValue()
            }
        }

        stdoutJob.join()
        stderrJob.join()

        ProcessResult(
            exitCode = exitCode,
            stdout = stdoutBuilder.toString().trimEnd('\n'),
            stderr = stderrBuilder.toString().trimEnd('\n'),
        )
    }

    override fun stream(
        command: List<String>,
        workDir: FilePath,
        env: Map<String, String>,
    ): Flow<String> = callbackFlow {
        val builder = ProcessBuilder(command)
            .directory(File(workDir.path))
            .redirectErrorStream(true) // merge stdout+stderr

        builder.environment().clear()
        builder.environment().putAll(env)

        val process = builder.start()

        val readerJob = launch(Dispatchers.IO) {
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    trySend(line)
                    line = reader.readLine()
                }
            }
        }

        awaitClose {
            readerJob.cancel()
            process.destroyForcibly()
        }
    }
}

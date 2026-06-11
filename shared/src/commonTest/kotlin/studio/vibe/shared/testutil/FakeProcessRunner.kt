package studio.vibe.shared.testutil

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import studio.vibe.shared.core.common.ProcessResult
import studio.vibe.shared.core.common.ProcessRunner
import studio.vibe.shared.core.common.FilePath
import kotlin.time.Duration

/**
 * Test double for [ProcessRunner].
 *
 * Records every invocation for assertion purposes and returns the response
 * configured via [respondWith] / [respondWithError].
 *
 * Each call pops the first queued response. If the queue is exhausted the last
 * response is repeated, which simplifies tests that call the same command many
 * times (e.g. polling tests).
 */
class FakeProcessRunner : ProcessRunner {

    data class Invocation(
        val command: List<String>,
        val workDir: FilePath,
        val env: Map<String, String>,
        val timeout: Duration,
    )

    private val responseQueue = mutableListOf<Result<ProcessResult>>()
    val invocations = mutableListOf<Invocation>()

    /** Queue a successful [ProcessResult]. */
    fun respondWith(exitCode: Int = 0, stdout: String = "", stderr: String = "") {
        responseQueue.add(Result.success(ProcessResult(exitCode, stdout, stderr)))
    }

    /** Queue a thrown exception (simulates process-runner crash / binary-not-found). */
    fun respondWithError(throwable: Throwable = RuntimeException("process runner error")) {
        responseQueue.add(Result.failure(throwable))
    }

    override suspend fun run(
        command: List<String>,
        workDir: FilePath,
        env: Map<String, String>,
        timeout: Duration,
    ): ProcessResult {
        invocations.add(Invocation(command, workDir, env, timeout))
        val response = if (responseQueue.size > 1) responseQueue.removeFirst() else responseQueue.firstOrNull()
            ?: Result.success(ProcessResult(0, "", ""))
        return response.getOrThrow()
    }

    override fun stream(
        command: List<String>,
        workDir: FilePath,
        env: Map<String, String>,
    ): Flow<String> = emptyFlow()

    fun reset() {
        responseQueue.clear()
        invocations.clear()
    }
}

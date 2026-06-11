package studio.vibe.shared.core.common

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

interface ProcessRunner {
    suspend fun run(
        command: List<String>,
        workDir: FilePath,
        env: Map<String, String> = emptyMap(),
        timeout: Duration = Duration.INFINITE,
    ): ProcessResult

    fun stream(
        command: List<String>,
        workDir: FilePath,
        env: Map<String, String> = emptyMap(),
    ): Flow<String>
}

package studio.vibe.shared.feature.git.data.executor

import studio.vibe.shared.feature.git.domain.contract.GitCommitting
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.feature.git.domain.model.GitServiceError

/** Implements [GitCommitting] — create commits. */
internal class GitCommitExecutor(private val runner: GitProcessRunner) : GitCommitting {

    override suspend fun commit(message: String, at: FilePath): String {
        if (message.trim().isEmpty()) {
            throw GitServiceError.CommandFailed(
                command = "commit",
                exitCode = 1,
                stderr = "Commit message cannot be empty",
            )
        }
        val output = runner.runGit(listOf("commit", "-m", message), at)
        // Extract commit hash from output (first 7–40 hex chars).
        val match = Regex("[0-9a-f]{7,40}").find(output)
        return match?.value ?: output
    }
}

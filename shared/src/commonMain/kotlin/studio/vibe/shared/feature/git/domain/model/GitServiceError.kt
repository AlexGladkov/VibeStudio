package studio.vibe.shared.feature.git.domain.model

import studio.vibe.shared.core.common.FilePath

sealed class GitServiceError(override val message: String) : Exception(message) {
    data object GitNotFound : GitServiceError("git executable not found")
    data class NotARepository(val path: FilePath) : GitServiceError("Not a git repository: ${path.path}")
    data class CommandFailed(val command: String, val exitCode: Int, val stderr: String) : GitServiceError("git $command failed (exit $exitCode): $stderr")
    data class Timeout(val command: String, val seconds: Int) : GitServiceError("git $command timed out after ${seconds}s")
    data class MergeConflict(val files: List<String>) : GitServiceError("Merge conflict in ${files.size} file(s): ${files.joinToString(", ")}")
    data class PushRejected(val reason: String) : GitServiceError("Push rejected: $reason")
    data class ParseError(val command: String, val output: String) : GitServiceError("Failed to parse output of git $command")
}

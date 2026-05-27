package studio.vibe.shared.model

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Project Manager Errors
@OptIn(ExperimentalUuidApi::class)
sealed class ProjectManagerError(override val message: String) : Exception(message) {
    data class InvalidPath(val path: FilePath) : ProjectManagerError("Path does not exist or is not a directory: ${path.path}")
    data class Duplicate(val existingId: Uuid, val path: FilePath) : ProjectManagerError("Project already exists at: ${path.path}")
    data class NotFound(val id: Uuid) : ProjectManagerError("Project not found: $id")
    data class PersistenceFailed(val underlying: Throwable) : ProjectManagerError("Failed to persist project list: ${underlying.message}")
    data class ProjectLimitReached(val max: Int) : ProjectManagerError("Maximum number of projects reached: $max")
}

// Terminal Session Errors
@OptIn(ExperimentalUuidApi::class)
sealed class TerminalSessionError(override val message: String) : Exception(message) {
    data class PtyCreationFailed(val reason: String) : TerminalSessionError("PTY creation failed: $reason")
    data class SessionNotFound(val id: Uuid) : TerminalSessionError("Terminal session not found: $id")
    data class ProjectNotFound(val id: Uuid) : TerminalSessionError("Project not found for terminal session: $id")
    data class SessionAlreadyExited(val sessionId: Uuid, val exitCode: Int) : TerminalSessionError("Session $sessionId already exited with code $exitCode")
    data class SessionLimitReached(val projectId: Uuid, val max: Int) : TerminalSessionError("Session limit ($max) reached for project $projectId")
    data class ShellNotFound(val path: String) : TerminalSessionError("Shell not found at: $path")
}

// Git Service Errors
sealed class GitServiceError(override val message: String) : Exception(message) {
    data object GitNotFound : GitServiceError("git executable not found")
    data class NotARepository(val path: FilePath) : GitServiceError("Not a git repository: ${path.path}")
    data class CommandFailed(val command: String, val exitCode: Int, val stderr: String) : GitServiceError("git $command failed (exit $exitCode): $stderr")
    data class Timeout(val command: String, val seconds: Int) : GitServiceError("git $command timed out after ${seconds}s")
    data class MergeConflict(val files: List<String>) : GitServiceError("Merge conflict in ${files.size} file(s): ${files.joinToString(", ")}")
    data class PushRejected(val reason: String) : GitServiceError("Push rejected: $reason")
    data class ParseError(val command: String, val output: String) : GitServiceError("Failed to parse output of git $command")
}

// AI Commit Service Errors
sealed class AICommitServiceError(override val message: String) : Exception(message) {
    data object MissingAPIKey : AICommitServiceError("ANTHROPIC_API_KEY not set in environment")
    data class ApiError(val statusCode: Int) : AICommitServiceError("Anthropic API returned status $statusCode")
    data object InvalidResponseFormat : AICommitServiceError("Invalid API response format")
    data object InvalidConfiguration : AICommitServiceError("Invalid AI service configuration")
}

// Agent Errors
sealed class AgentError(override val message: String) : Exception(message) {
    data class ExecutableNotFound(val agent: String) : AgentError("CLI executable not found for agent: $agent")
    data class MissingAPIKey(val agent: String, val envVar: String) : AgentError("API key $envVar not set for agent: $agent")
}

// File System Watcher Errors
sealed class FileSystemWatcherError(override val message: String) : Exception(message) {
    data class StreamCreationFailed(val path: FilePath) : FileSystemWatcherError("Failed to create file watch for: ${path.path}")
    data class PathNotFound(val path: FilePath) : FileSystemWatcherError("Watch path does not exist: ${path.path}")
    data class AlreadyWatching(val path: FilePath) : FileSystemWatcherError("Already watching: ${path.path}")
}

// Session Persistence Errors
@OptIn(ExperimentalUuidApi::class)
sealed class SessionPersistenceError(override val message: String) : Exception(message) {
    data class EncodingFailed(val underlying: Throwable) : SessionPersistenceError("Session encoding failed: ${underlying.message}")
    data class DecodingFailed(val underlying: Throwable) : SessionPersistenceError("Session decoding failed: ${underlying.message}")
    data class SnapshotCorrupted(val path: FilePath) : SessionPersistenceError("Session snapshot corrupted: ${path.path}")
    data class IncompatibleVersion(val found: Int, val expected: Int) : SessionPersistenceError("Snapshot version $found is incompatible (expected $expected)")
    data class ScrollbackWriteFailed(val sessionId: Uuid, val underlying: Throwable) : SessionPersistenceError("Scrollback write failed for session $sessionId: ${underlying.message}")
}

// Path Constants Error
sealed class PathConstantsError(override val message: String) : Exception(message) {
    data object AppSupportNotFound : PathConstantsError("Application Support directory not found on this system")
}

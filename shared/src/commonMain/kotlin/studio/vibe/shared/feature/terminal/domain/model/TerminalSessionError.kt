package studio.vibe.shared.feature.terminal.domain.model

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
sealed class TerminalSessionError(override val message: String) : Exception(message) {
    data class PtyCreationFailed(val reason: String) : TerminalSessionError("PTY creation failed: $reason")
    data class SessionNotFound(val id: Uuid) : TerminalSessionError("Terminal session not found: $id")
    data class ProjectNotFound(val id: Uuid) : TerminalSessionError("Project not found for terminal session: $id")
    data class SessionAlreadyExited(val sessionId: Uuid, val exitCode: Int) : TerminalSessionError("Session $sessionId already exited with code $exitCode")
    data class SessionLimitReached(val projectId: Uuid, val max: Int) : TerminalSessionError("Session limit ($max) reached for project $projectId")
    data class ShellNotFound(val path: String) : TerminalSessionError("Shell not found at: $path")
}

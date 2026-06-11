package studio.vibe.shared.core.common.terminal

import studio.vibe.shared.core.common.AIAgent
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.core.common.terminal.SplitDirection
import studio.vibe.shared.core.common.terminal.TerminalSession
import studio.vibe.shared.core.common.terminal.TerminalSize
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
interface TerminalSessionCreating {
    fun createSession(
        projectId: Uuid,
        shell: String? = null,
        workingDirectory: FilePath? = null,
        size: TerminalSize = TerminalSize(columns = 80, rows = 24),
    ): TerminalSession

    fun resize(sessionId: Uuid, size: TerminalSize)

    fun killSession(sessionId: Uuid, force: Boolean = false)

    fun killAllSessions(projectId: Uuid)

    fun split(
        sessionId: Uuid,
        direction: SplitDirection,
        size: TerminalSize = TerminalSize(columns = 80, rows = 24),
    ): TerminalSession

    /**
     * Spawn a new agent CLI session for [agent] inside [projectId].
     *
     * Failures (binary missing, PTY creation failed, session-limit hit, etc.)
     * are wrapped in [Result.failure] so callers can surface the original
     * exception's message to the user. Earlier returns of plain `null` made
     * every failure look identical from the UI.
     */
    fun startAgentSession(
        agent: AIAgent,
        projectId: Uuid,
        workingDirectory: String,
        apiKeyValue: String?,
    ): Result<TerminalSession>
}

@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.remote

import studio.vibe.shared.model.TerminalSession
import studio.vibe.shared.model.TerminalSessionState
import java.util.UUID

/**
 * Maps a [TerminalSession] to a [SessionResponse] DTO.
 *
 * Extracted to eliminate the 4× duplication that existed in [RemoteControlServer]
 * at lines ~461, ~505, ~534, ~585 (all /projects and /sessions endpoints).
 *
 * [activeBridges] is passed as a lambda to keep this mapper stateless and easily testable.
 */
internal object SessionResponseMapper {

    /**
     * Converts [session] to a [SessionResponse] using the provided [activeBridges] snapshot.
     *
     * @param session       The terminal session to map.
     * @param activeBridges Lambda returning the current set of active [RemoteSessionBridge] values.
     *                      Passed as a lambda so the mapper always reads the latest snapshot.
     */
    fun map(
        session: TerminalSession,
        activeBridges: () -> Collection<RemoteSessionBridge>,
    ): SessionResponse {
        val bridges = activeBridges()
        val sessionIdStr = session.id.toString()
        return SessionResponse(
            id = sessionIdStr,
            title = session.title,
            state = when (session.state) {
                is TerminalSessionState.Running -> "running"
                is TerminalSessionState.Exited -> "exited"
                else -> "unknown"
            },
            isAgent = session.isAgentSession,
            hasRemoteAttachment = bridges.any { it.sessionId.toString() == sessionIdStr },
            attachedDeviceId = bridges
                .firstOrNull { it.sessionId.toString() == sessionIdStr }
                ?.deviceId?.toString(),
        )
    }
}

package studio.vibe.shared.feature.terminal.domain.contract

import kotlinx.coroutines.flow.Flow
import studio.vibe.shared.core.common.terminal.TerminalSize
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Minimal terminal surface needed by the Remote Control layer.
 *
 * By depending on this interface instead of the concrete [DesktopTerminalService],
 * [RemoteControlServer] and [RemoteSessionBridge] remain portable across targets
 * (desktop JVM, future macOS native). Implementations must be thread-safe.
 *
 * [outputFlow] returns a hot [Flow] of raw PTY output
 * chunks for the given session, or `null` if the session does not exist.
 */
@OptIn(ExperimentalUuidApi::class)
interface TerminalRemoteHost :
    TerminalSessionQuerying,
    TerminalInputSending {

    /**
     * Returns a hot [Flow] of raw PTY output strings
     * for [sessionId], or `null` when the session is not found.
     */
    fun outputFlow(sessionId: Uuid): Flow<String>?

    fun resize(sessionId: Uuid, size: TerminalSize)
}

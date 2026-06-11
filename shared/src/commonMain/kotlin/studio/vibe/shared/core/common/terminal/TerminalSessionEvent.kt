@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.core.common.terminal

import kotlin.uuid.Uuid

sealed class TerminalSessionEvent {
    data class ActivityDetected(val sessionId: Uuid, val projectId: Uuid) : TerminalSessionEvent()
    data class ProcessExited(val sessionId: Uuid, val projectId: Uuid, val exitCode: Int) : TerminalSessionEvent()
    data class TitleChanged(val sessionId: Uuid, val newTitle: String) : TerminalSessionEvent()
    data class Bell(val sessionId: Uuid) : TerminalSessionEvent()
}

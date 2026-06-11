package studio.vibe.shared.core.common.terminal

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
interface TerminalScrollbackAccessing {
    fun scrollbackContent(sessionId: Uuid): String?
}

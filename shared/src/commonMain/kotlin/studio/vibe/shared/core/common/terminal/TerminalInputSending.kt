package studio.vibe.shared.core.common.terminal

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
interface TerminalInputSending {
    fun sendInput(text: String, sessionId: Uuid)
}

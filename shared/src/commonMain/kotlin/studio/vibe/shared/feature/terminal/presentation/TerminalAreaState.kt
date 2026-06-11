package studio.vibe.shared.feature.terminal.presentation

import studio.vibe.shared.core.common.terminal.TerminalSession
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class TerminalAreaState(
    val sessions: List<TerminalSession> = emptyList(),
    val activeSessionId: Uuid? = null,
    val errorMessage: String? = null,
)

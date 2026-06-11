@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.terminal.domain.usecase

import kotlin.uuid.Uuid

data class CloseTerminalSessionParams(
    val sessionId: Uuid,
    val force: Boolean = false,
)

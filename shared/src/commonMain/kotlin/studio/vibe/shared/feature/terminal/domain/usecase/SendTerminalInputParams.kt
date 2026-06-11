@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.terminal.domain.usecase

import kotlin.uuid.Uuid

/**
 * Parameters for [SendTerminalInputUseCase].
 *
 * @param text      The text (including control characters / newlines) to send.
 * @param sessionId The target terminal session.
 */
data class SendTerminalInputParams(
    val text: String,
    val sessionId: Uuid,
)

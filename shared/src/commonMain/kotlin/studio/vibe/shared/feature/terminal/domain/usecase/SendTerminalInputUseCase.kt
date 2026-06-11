@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.terminal.domain.usecase

import studio.vibe.shared.core.common.UseCase
import studio.vibe.shared.core.common.terminal.TerminalSessionManaging

/**
 * Use case that sends keyboard input to a running terminal session.
 *
 * Isolates [TerminalSessionManaging] from [TerminalAreaViewModel].
 */
class SendTerminalInputUseCase(
    private val terminalSessionManaging: TerminalSessionManaging,
) : UseCase<SendTerminalInputParams, Unit> {

    override suspend fun invoke(params: SendTerminalInputParams): Result<Unit> = runCatching {
        terminalSessionManaging.sendInput(params.text, params.sessionId)
    }
}

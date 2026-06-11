@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.terminal.domain.usecase

import studio.vibe.shared.core.common.UseCase
import studio.vibe.shared.core.common.terminal.TerminalSessionManaging

/**
 * Use case that closes (kills) a running terminal session.
 *
 * Isolates [TerminalSessionManaging] from [TerminalAreaViewModel].
 */
class CloseTerminalSessionUseCase(
    private val terminalSessionManaging: TerminalSessionManaging,
) : UseCase<CloseTerminalSessionParams, Unit> {

    override suspend fun invoke(params: CloseTerminalSessionParams): Result<Unit> = runCatching {
        terminalSessionManaging.killSession(params.sessionId, params.force)
    }
}

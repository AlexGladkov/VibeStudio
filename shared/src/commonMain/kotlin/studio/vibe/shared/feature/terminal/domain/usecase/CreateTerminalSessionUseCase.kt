@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.terminal.domain.usecase

import studio.vibe.shared.core.common.UseCase
import studio.vibe.shared.core.common.terminal.TerminalSessionManaging
import studio.vibe.shared.core.common.terminal.TerminalSession

/**
 * Use case that creates a new terminal session for a project.
 *
 * Isolates [TerminalSessionManaging] from [TerminalAreaViewModel] so the ViewModel
 * depends only on the use case contract.
 */
class CreateTerminalSessionUseCase(
    private val terminalSessionManaging: TerminalSessionManaging,
) : UseCase<CreateTerminalSessionParams, TerminalSession> {

    override suspend fun invoke(params: CreateTerminalSessionParams): Result<TerminalSession> =
        runCatching {
            terminalSessionManaging.createSession(
                projectId = params.projectId,
                shell = params.shell,
                workingDirectory = params.workingDirectory,
                size = params.size,
            )
        }
}

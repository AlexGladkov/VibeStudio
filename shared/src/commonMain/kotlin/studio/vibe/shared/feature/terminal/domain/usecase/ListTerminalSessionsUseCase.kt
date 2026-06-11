@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.terminal.domain.usecase

import studio.vibe.shared.core.common.UseCase
import studio.vibe.shared.core.common.terminal.TerminalSessionManaging
import studio.vibe.shared.core.common.terminal.TerminalSession

/**
 * Use case that returns the current terminal sessions for a project.
 *
 * Isolates [TerminalSessionManaging] from [TerminalAreaViewModel] so the ViewModel
 * holds no direct reference to the infrastructure layer.
 */
class ListTerminalSessionsUseCase(
    private val terminalSessionManaging: TerminalSessionManaging,
) : UseCase<ListTerminalSessionsParams, List<TerminalSession>> {

    override suspend fun invoke(params: ListTerminalSessionsParams): Result<List<TerminalSession>> =
        runCatching {
            terminalSessionManaging.sessions(params.projectId)
        }
}

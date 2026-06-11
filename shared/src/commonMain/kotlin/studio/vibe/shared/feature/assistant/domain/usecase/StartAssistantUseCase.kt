@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.assistant.domain.usecase

import studio.vibe.shared.core.common.UseCase
import studio.vibe.shared.core.common.terminal.TerminalSession
import studio.vibe.shared.feature.assistant.domain.contract.AssistantLaunching

/**
 * Use case that starts an AI agent for a given project.
 *
 * Delegates to [AssistantLaunching.start] and wraps the result so callers receive
 * a [Result]-typed value without catching raw exceptions.
 */
class StartAssistantUseCase(
    private val assistantLaunching: AssistantLaunching,
) : UseCase<StartAssistantParams, TerminalSession> {

    override suspend fun invoke(params: StartAssistantParams): Result<TerminalSession> =
        assistantLaunching.start(
            projectId = params.projectId,
            agentId = params.agentId,
            resume = params.resume,
        )
}

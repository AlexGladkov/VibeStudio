@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.assistant.domain.usecase

import studio.vibe.shared.core.common.UseCase
import studio.vibe.shared.feature.assistant.domain.contract.AssistantLaunching

/**
 * Use case that stops a running AI agent for a given project.
 *
 * Delegates to [AssistantLaunching.stop] and wraps the result so callers receive
 * a [Result]-typed value without catching raw exceptions.
 */
class StopAssistantUseCase(
    private val assistantLaunching: AssistantLaunching,
) : UseCase<StopAssistantParams, Unit> {

    override suspend fun invoke(params: StopAssistantParams): Result<Unit> =
        assistantLaunching.stop(
            projectId = params.projectId,
            agentId = params.agentId,
        )
}

@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.assistant.domain.usecase

import kotlin.uuid.Uuid

/**
 * Parameters for [StartAssistantUseCase].
 *
 * @param projectId   The project in which the agent should be launched.
 * @param agentId     The identifier of the agent to start.
 * @param resume      Optional resume request to continue a previous conversation.
 */
data class StartAssistantParams(
    val projectId: Uuid,
    val agentId: String,
    val resume: ResumeRequest? = null,
)

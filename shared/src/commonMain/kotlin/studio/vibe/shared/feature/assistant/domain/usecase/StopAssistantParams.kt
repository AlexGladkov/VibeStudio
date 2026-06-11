@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.assistant.domain.usecase

import kotlin.uuid.Uuid

/**
 * Parameters for [StopAssistantUseCase].
 *
 * @param projectId The project in which the agent is running.
 * @param agentId   The identifier of the running agent to stop.
 */
data class StopAssistantParams(
    val projectId: Uuid,
    val agentId: String,
)

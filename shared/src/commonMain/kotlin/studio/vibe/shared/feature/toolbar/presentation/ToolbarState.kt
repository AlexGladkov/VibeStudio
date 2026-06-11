package studio.vibe.shared.feature.toolbar.presentation

import studio.vibe.shared.core.common.AIAgent
import studio.vibe.shared.core.common.AgentAvailabilityStatus
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class ToolbarState(
    val selectedAgent: AIAgent? = null,
    val isAgentRunning: Boolean = false,
    val activeAgentSessionId: Uuid? = null,
    val agentAvailability: Map<AIAgent, AgentAvailabilityStatus> = emptyMap(),
    val errorMessage: String? = null,
    val isCheckingAvailability: Boolean = false,
)

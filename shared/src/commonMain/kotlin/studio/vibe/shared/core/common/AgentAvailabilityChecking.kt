package studio.vibe.shared.core.common

import kotlinx.coroutines.flow.StateFlow

sealed class AgentAvailabilityStatus {
    data class Available(val path: String, val hasAPIKey: Boolean) : AgentAvailabilityStatus()
    data class NotInstalled(val installHint: String) : AgentAvailabilityStatus()
    data object Checking : AgentAvailabilityStatus()
}

interface AgentAvailabilityChecking {
    /**
     * Reactive snapshot of every registered agent's status, keyed by [AIAgent].
     * Plugin-supplied agents appear here as soon as they are registered with the
     * application's [AIAgentRegistry].
     */
    val availabilityFlow: StateFlow<Map<AIAgent, AgentAvailabilityStatus>>

    /** Eager snapshot for non-Composable callers. */
    val availability: Map<AIAgent, AgentAvailabilityStatus>
        get() = availabilityFlow.value

    fun refreshAll()
    fun check(agent: AIAgent): AgentAvailabilityStatus
    fun canLaunch(agent: AIAgent): Boolean
}

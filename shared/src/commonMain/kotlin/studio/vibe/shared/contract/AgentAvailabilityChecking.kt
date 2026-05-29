package studio.vibe.shared.contract

import kotlinx.coroutines.flow.StateFlow
import studio.vibe.shared.model.AIAssistant

sealed class AgentAvailabilityStatus {
    data class Available(val path: String, val hasAPIKey: Boolean) : AgentAvailabilityStatus()
    data class NotInstalled(val installHint: String) : AgentAvailabilityStatus()
    data object Checking : AgentAvailabilityStatus()
}

interface AgentAvailabilityChecking {
    /**
     * Reactive snapshot of every agent's status. Observers (the toolbar VM,
     * the install sheet, etc.) should collect this flow instead of polling
     * the eager `availability` map below — the flow guarantees that an entry
     * flips from [AgentAvailabilityStatus.Checking] to its final state the
     * instant the background probe completes.
     */
    val availabilityFlow: StateFlow<Map<AIAssistant, AgentAvailabilityStatus>>

    /** Eager snapshot for non-Composable callers. Equivalent to `availabilityFlow.value`. */
    val availability: Map<AIAssistant, AgentAvailabilityStatus>
        get() = availabilityFlow.value

    fun refreshAll()
    fun check(agent: AIAssistant): AgentAvailabilityStatus
    fun canLaunch(agent: AIAssistant): Boolean
}

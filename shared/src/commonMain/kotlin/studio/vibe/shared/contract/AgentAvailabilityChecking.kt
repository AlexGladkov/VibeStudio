package studio.vibe.shared.contract

import studio.vibe.shared.model.AIAssistant

sealed class AgentAvailabilityStatus {
    data class Available(val path: String, val hasAPIKey: Boolean) : AgentAvailabilityStatus()
    data class NotInstalled(val installHint: String) : AgentAvailabilityStatus()
    data object Checking : AgentAvailabilityStatus()
}

interface AgentAvailabilityChecking {
    val availability: Map<AIAssistant, AgentAvailabilityStatus>
    fun refreshAll()
    fun check(agent: AIAssistant): AgentAvailabilityStatus
    fun canLaunch(agent: AIAssistant): Boolean
}

package studio.vibe.shared.testutil

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import studio.vibe.shared.core.common.AIAgent
import studio.vibe.shared.core.common.AgentAvailabilityChecking
import studio.vibe.shared.core.common.AgentAvailabilityStatus

/**
 * In-memory [AgentAvailabilityChecking] for VM unit tests.
 *
 * Tests can mutate [setAvailability] to drive arbitrary states.
 */
class FakeAgentAvailabilityChecking(
    initial: Map<AIAgent, AgentAvailabilityStatus> = emptyMap(),
) : AgentAvailabilityChecking {

    private val _flow = MutableStateFlow(initial)
    override val availabilityFlow: StateFlow<Map<AIAgent, AgentAvailabilityStatus>> = _flow.asStateFlow()

    var refreshCallCount: Int = 0

    fun setAvailability(agent: AIAgent, status: AgentAvailabilityStatus) {
        _flow.update { it + (agent to status) }
    }

    fun setAllAvailable(agents: List<AIAgent>) {
        _flow.value = agents.associateWith {
            AgentAvailabilityStatus.Available(path = "/usr/bin/${it.executableName}", hasAPIKey = true)
        }
    }

    override fun refreshAll() {
        refreshCallCount++
    }

    override fun check(agent: AIAgent): AgentAvailabilityStatus =
        _flow.value[agent] ?: AgentAvailabilityStatus.Checking

    override fun canLaunch(agent: AIAgent): Boolean =
        check(agent) is AgentAvailabilityStatus.Available
}

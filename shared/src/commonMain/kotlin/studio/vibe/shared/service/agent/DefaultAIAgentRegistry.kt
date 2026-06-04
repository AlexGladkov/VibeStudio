package studio.vibe.shared.service.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import studio.vibe.shared.contract.AIAgent
import studio.vibe.shared.contract.AIAgentRegistry

/**
 * Default [AIAgentRegistry] seeded with all built-in agents.
 *
 * Thread-safe: registration goes through [MutableStateFlow.update] so concurrent
 * `register` / `unregister` calls observe a consistent view.
 */
class DefaultAIAgentRegistry(
    initialAgents: List<AIAgent> = BuiltInAgents,
) : AIAgentRegistry {

    private val _agents = MutableStateFlow(initialAgents)
    override val agents: StateFlow<List<AIAgent>> = _agents.asStateFlow()

    override fun byId(id: String): AIAgent? = _agents.value.firstOrNull { it.id == id }

    override fun register(agent: AIAgent): Boolean {
        var added = false
        _agents.update { current ->
            if (current.any { it.id == agent.id }) {
                added = false
                current
            } else {
                added = true
                current + agent
            }
        }
        return added
    }

    override fun unregister(id: String): Boolean {
        var removed = false
        _agents.update { current ->
            val next = current.filterNot { it.id == id }
            removed = next.size != current.size
            next
        }
        return removed
    }
}

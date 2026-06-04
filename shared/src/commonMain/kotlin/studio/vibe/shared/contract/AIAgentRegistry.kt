package studio.vibe.shared.contract

import kotlinx.coroutines.flow.StateFlow
import studio.vibe.shared.model.AIAssistant

/**
 * Source of truth for the set of AI agents available in the application.
 *
 * Consumers (ToolbarViewModel, AgentAvailabilityServiceImpl, settings UI)
 * read from this registry instead of branching on [AIAssistant], so adding a
 * new agent does not require modifying their logic.
 */
interface AIAgentRegistry {

    /** Live list of currently registered agents. */
    val agents: StateFlow<List<AIAgent>>

    /** Snapshot of the registered agents. */
    fun snapshot(): List<AIAgent> = agents.value

    fun byId(id: String): AIAgent?

    /**
     * Lookup helper for code paths still typed on [AIAssistant] — returns the
     * matching built-in agent if any has [AIAgent.legacyAssistant] equal to
     * [assistant].
     */
    fun byAssistant(assistant: AIAssistant): AIAgent?

    /**
     * Add a new agent.  Returns `true` if the agent was added, `false` if an
     * agent with the same [AIAgent.id] is already registered.
     */
    fun register(agent: AIAgent): Boolean

    /** Remove the agent with the given id.  Returns `true` if anything was removed. */
    fun unregister(id: String): Boolean
}

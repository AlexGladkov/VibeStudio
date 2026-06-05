package studio.vibe.shared.service.agent

import studio.vibe.shared.contract.AIAgent
import studio.vibe.shared.model.AgentExitSequence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultAIAgentRegistryTest {

    private fun makeAgent(id: String) = object : AIAgent {
        override val id = id
        override val displayName = "Agent $id"
        override val executableName = id
        override val launchCommand = id
        override val apiKeyEnvironmentVariable: String? = null
        override val installHint = ""
        override val shortDescription = ""
        override val prerequisite: String? = null
        override val prerequisiteCheckCommand: String? = null
        override val setupInstructions: String? = null
    }

    @Test
    fun register_newAgent_returnsTrue_andAppearsInSnapshot() {
        val registry = DefaultAIAgentRegistry(initialAgents = emptyList())
        val agent = makeAgent("test-agent")

        val added = registry.register(agent)

        assertTrue(added)
        assertEquals(1, registry.agents.value.size)
        assertEquals("test-agent", registry.agents.value.first().id)
    }

    @Test
    fun register_duplicateId_returnsFalse_andDoesNotAddDuplicate() {
        val agent = makeAgent("dup")
        val registry = DefaultAIAgentRegistry(initialAgents = listOf(agent))

        val added = registry.register(makeAgent("dup"))

        assertFalse(added)
        assertEquals(1, registry.agents.value.size)
    }

    @Test
    fun byId_knownId_returnsAgent() {
        val agent = makeAgent("claude")
        val registry = DefaultAIAgentRegistry(initialAgents = listOf(agent))

        val found = registry.byId("claude")

        assertEquals(agent.id, found?.id)
    }

    @Test
    fun byId_unknownId_returnsNull() {
        val registry = DefaultAIAgentRegistry(initialAgents = emptyList())

        assertNull(registry.byId("nonexistent"))
    }

    @Test
    fun unregister_knownId_returnsTrue_andRemovesFromSnapshot() {
        val agent = makeAgent("to-remove")
        val registry = DefaultAIAgentRegistry(initialAgents = listOf(agent))

        val removed = registry.unregister("to-remove")

        assertTrue(removed)
        assertTrue(registry.agents.value.isEmpty())
    }

    @Test
    fun unregister_unknownId_returnsFalse_andLeavesListUnchanged() {
        val agent = makeAgent("keep")
        val registry = DefaultAIAgentRegistry(initialAgents = listOf(agent))

        val removed = registry.unregister("does-not-exist")

        assertFalse(removed)
        assertEquals(1, registry.agents.value.size)
    }

    @Test
    fun agents_initializedWithBuiltIns_containsExpectedCount() {
        val registry = DefaultAIAgentRegistry()

        // BuiltInAgents list is non-empty — exact count may change; just verify non-zero
        assertTrue(registry.agents.value.isNotEmpty())
    }

    @Test
    fun register_multiple_appendsAllAgents() {
        val registry = DefaultAIAgentRegistry(initialAgents = emptyList())

        registry.register(makeAgent("a"))
        registry.register(makeAgent("b"))
        registry.register(makeAgent("c"))

        assertEquals(3, registry.agents.value.size)
    }
}

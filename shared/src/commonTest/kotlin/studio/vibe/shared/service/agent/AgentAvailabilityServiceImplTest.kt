@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package studio.vibe.shared.core.common

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import studio.vibe.shared.core.common.AIAgent
import studio.vibe.shared.core.common.AgentAvailabilityStatus
import studio.vibe.shared.core.common.BinaryResolver
import studio.vibe.shared.core.common.CredentialStorage
import studio.vibe.shared.core.common.FilePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AgentAvailabilityServiceImplTest {

    // ── Test doubles ──────────────────────────────────────────────────────────────

    private fun makeAgent(id: String, exe: String = id, apiKey: String? = null) = object : AIAgent {
        override val id = id
        override val displayName = "Agent $id"
        override val executableName = exe
        override val launchCommand = exe
        override val apiKeyEnvironmentVariable = apiKey
        override val installHint = "install $id"
        override val shortDescription = ""
        override val prerequisite: String? = null
        override val prerequisiteCheckCommand: String? = null
        override val setupInstructions: String? = null
    }

    private class FakeBinaryResolver(private val found: Set<String> = emptySet()) : BinaryResolver {
        override fun findExecutable(name: String): FilePath? =
            if (name in found) FilePath("/usr/bin/$name") else null
    }

    private class FakeCredentialStorage(private val stored: Map<String, String> = emptyMap()) : CredentialStorage {
        override suspend fun save(account: String, value: String) {}
        override suspend fun load(account: String): String? = stored[account]
        override suspend fun delete(account: String) {}
    }

    // ── refreshAll() / check() ────────────────────────────────────────────────────

    @Test
    fun check_beforeRefresh_returnsChecking() = runTest {
        val agent = makeAgent("claude", "claude", "ANTHROPIC_API_KEY")
        val registry = DefaultAIAgentRegistry(listOf(agent))
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val service = AgentAvailabilityServiceImpl(
            binaryResolver = FakeBinaryResolver(),
            credentialStorage = FakeCredentialStorage(),
            registry = registry,
            scope = scope,
        )

        // Before any refresh, status should be Checking
        assertEquals(AgentAvailabilityStatus.Checking, service.check(agent))
    }

    @Test
    fun refreshAll_binaryFound_withKey_returnsAvailableWithApiKey() = runTest {
        val agent = makeAgent("claude", "claude", "ANTHROPIC_API_KEY")
        val registry = DefaultAIAgentRegistry(listOf(agent))
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val service = AgentAvailabilityServiceImpl(
            binaryResolver = FakeBinaryResolver(found = setOf("claude")),
            credentialStorage = FakeCredentialStorage(stored = mapOf("ANTHROPIC_API_KEY" to "sk-test")),
            registry = registry,
            scope = scope,
        )

        service.refreshAll()
        scope.advanceUntilIdle()

        val status = service.check(agent)
        assertIs<AgentAvailabilityStatus.Available>(status)
        assertTrue((status as AgentAvailabilityStatus.Available).hasAPIKey)
    }

    @Test
    fun refreshAll_binaryFound_missingApiKey_returnsAvailableWithoutKey() = runTest {
        val agent = makeAgent("claude", "claude", "ANTHROPIC_API_KEY")
        val registry = DefaultAIAgentRegistry(listOf(agent))
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val service = AgentAvailabilityServiceImpl(
            binaryResolver = FakeBinaryResolver(found = setOf("claude")),
            credentialStorage = FakeCredentialStorage(), // no stored key
            registry = registry,
            scope = scope,
        )

        service.refreshAll()
        scope.advanceUntilIdle()

        val status = service.check(agent)
        assertIs<AgentAvailabilityStatus.Available>(status)
        assertFalse((status as AgentAvailabilityStatus.Available).hasAPIKey)
    }

    @Test
    fun refreshAll_binaryNotFound_returnsNotInstalled() = runTest {
        val agent = makeAgent("claude", "claude")
        val registry = DefaultAIAgentRegistry(listOf(agent))
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val service = AgentAvailabilityServiceImpl(
            binaryResolver = FakeBinaryResolver(found = emptySet()),
            credentialStorage = FakeCredentialStorage(),
            registry = registry,
            scope = scope,
        )

        service.refreshAll()
        scope.advanceUntilIdle()

        val status = service.check(agent)
        assertIs<AgentAvailabilityStatus.NotInstalled>(status)
    }

    @Test
    fun canLaunch_notAvailable_returnsFalse() = runTest {
        val agent = makeAgent("codex", "codex")
        val registry = DefaultAIAgentRegistry(listOf(agent))
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val service = AgentAvailabilityServiceImpl(
            binaryResolver = FakeBinaryResolver(found = emptySet()),
            credentialStorage = FakeCredentialStorage(),
            registry = registry,
            scope = scope,
        )

        service.refreshAll()
        scope.advanceUntilIdle()

        assertFalse(service.canLaunch(agent))
    }

    @Test
    fun refreshAll_concurrent_doesNotThrow() = runTest {
        val agent = makeAgent("claude", "claude")
        val registry = DefaultAIAgentRegistry(listOf(agent))
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val service = AgentAvailabilityServiceImpl(
            binaryResolver = FakeBinaryResolver(found = setOf("claude")),
            credentialStorage = FakeCredentialStorage(),
            registry = registry,
            scope = scope,
        )

        // Fire multiple concurrent refreshes — latest-wins cancellation must not throw
        repeat(5) { service.refreshAll() }
        scope.advanceUntilIdle()

        // After settling, status should be Available (binary found)
        assertIs<AgentAvailabilityStatus.Available>(service.check(agent))
    }
}

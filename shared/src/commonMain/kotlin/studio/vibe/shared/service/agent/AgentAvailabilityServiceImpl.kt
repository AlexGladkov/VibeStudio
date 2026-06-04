package studio.vibe.shared.service.agent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import studio.vibe.shared.contract.AIAgent
import studio.vibe.shared.contract.AIAgentRegistry
import studio.vibe.shared.contract.AgentAvailabilityChecking
import studio.vibe.shared.contract.AgentAvailabilityStatus
import studio.vibe.shared.contract.BinaryResolver
import studio.vibe.shared.contract.CredentialStorage
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Concrete implementation of [AgentAvailabilityChecking].
 *
 * Iterates over the agents reported by [AIAgentRegistry] — including built-in
 * agents and any plugin-registered agents — and probes each one for binary
 * presence + API-key availability.
 *
 * Thread-safety: [_availabilityFlow] mutations only happen on [scope].  Callers
 * should pick a single-confined [scope] (e.g. `Dispatchers.Main`) if UI bindings
 * require main-thread reads.
 */
class AgentAvailabilityServiceImpl(
    private val binaryResolver: BinaryResolver,
    private val credentialStorage: CredentialStorage,
    private val registry: AIAgentRegistry,
    private val scope: CoroutineScope,
) : AgentAvailabilityChecking {

    companion object {
        val CACHE_TTL = 30.seconds
    }

    private val _availabilityFlow: MutableStateFlow<Map<AIAgent, AgentAvailabilityStatus>> =
        MutableStateFlow(
            registry.snapshot().associateWith { AgentAvailabilityStatus.Checking },
        )

    override val availabilityFlow: StateFlow<Map<AIAgent, AgentAvailabilityStatus>> =
        _availabilityFlow.asStateFlow()

    private var lastRefreshAt: Instant = Instant.DISTANT_PAST

    init {
        // Re-seed availability whenever the registry changes (plugin add/remove).
        scope.launch {
            registry.agents.collect { current ->
                _availabilityFlow.value = current.associateWith { agent ->
                    _availabilityFlow.value[agent] ?: AgentAvailabilityStatus.Checking
                }
            }
        }
    }

    override fun refreshAll() {
        lastRefreshAt = Clock.System.now()

        scope.launch {
            val results = mutableMapOf<AIAgent, AgentAvailabilityStatus>()

            for (agent in registry.snapshot()) {
                val executableName = agent.executableName

                val resolvedPath = if (executableName.isNotEmpty() &&
                    !executableName.contains('/') &&
                    !executableName.contains("..")
                ) {
                    binaryResolver.findExecutable(executableName)?.path
                } else {
                    null
                }

                results[agent] = if (resolvedPath != null) {
                    AgentAvailabilityStatus.Available(
                        path = resolvedPath,
                        hasAPIKey = resolveApiKeyAvailability(agent),
                    )
                } else {
                    AgentAvailabilityStatus.NotInstalled(installHint = agent.installHint)
                }
            }

            _availabilityFlow.value = results.toMap()
        }
    }

    override fun check(agent: AIAgent): AgentAvailabilityStatus {
        val elapsed = Clock.System.now() - lastRefreshAt
        if (elapsed > CACHE_TTL) {
            refreshAll()
        }
        return _availabilityFlow.value[agent] ?: AgentAvailabilityStatus.Checking
    }

    override fun canLaunch(agent: AIAgent): Boolean {
        return check(agent) is AgentAvailabilityStatus.Available
    }

    private suspend fun resolveApiKeyAvailability(agent: AIAgent): Boolean {
        val envVar = agent.apiKeyEnvironmentVariable ?: return true
        val storedValue = credentialStorage.load(envVar)
        return !storedValue.isNullOrEmpty()
    }
}

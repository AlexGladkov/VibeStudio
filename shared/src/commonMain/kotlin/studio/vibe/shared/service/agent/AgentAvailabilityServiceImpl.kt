package studio.vibe.shared.service.agent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import studio.vibe.shared.contract.AIAgent
import studio.vibe.shared.contract.AIAgentRegistry
import studio.vibe.shared.contract.AgentAvailabilityChecking
import studio.vibe.shared.contract.AgentAvailabilityStatus
import studio.vibe.shared.contract.BinaryResolver
import studio.vibe.shared.contract.CredentialStorage
import kotlin.concurrent.Volatile
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

    @Volatile private var lastRefreshAt: Instant = Instant.DISTANT_PAST

    // "Latest wins" — cancel the previous refresh Job before launching a new one.
    // This prevents stale results from an older slow probe overwriting fresher data
    // when refreshAll() is called in rapid succession (e.g. project switches).
    @Volatile private var refreshJob: Job? = null

    // Guards access to [lastRefreshAt] and [refreshJob] from multiple threads.
    private val refreshMutex = Mutex()

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
        // The outer scope.launch is removed to fix a subtle race:
        // previously refreshJob?.cancel() executed, but the new refreshJob assignment
        // happened inside the outer launch — meaning a concurrent caller could see
        // the old refreshJob still alive when the outer launch hadn't run yet.
        //
        // Fix: single scope.launch that acquires the mutex, cancels+joins the previous
        // job (ensuring it has fully stopped), then launches doRefresh() as a child of
        // the current coroutine so it is tracked correctly.
        scope.launch {
            refreshMutex.withLock {
                lastRefreshAt = Clock.System.now()
                refreshJob?.cancelAndJoin()
                // Child launch inherits the current coroutine's scope so cancellation
                // propagates correctly, and the assignment is visible to subsequent
                // callers entering this mutex.
                refreshJob = launch { doRefresh() }
            }
        }
    }

    private suspend fun doRefresh() {
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
        // Clear refreshJob without re-acquiring refreshMutex — doing so would cause
        // an AB-BA deadlock: refreshAll() holds refreshMutex and calls cancelAndJoin()
        // on this job, but this job would then block waiting to re-acquire that same
        // mutex. A plain volatile write is safe because refreshAll() always
        // cancelAndJoin()s before reassigning, so the null write here is benign.
        refreshJob = null
    }

    override fun check(agent: AIAgent): AgentAvailabilityStatus {
        // lastRefreshAt read is best-effort (non-blocking path). Worst case we schedule
        // an extra refresh which is cheap. The mutex is only acquired in refreshAll/doRefresh.
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

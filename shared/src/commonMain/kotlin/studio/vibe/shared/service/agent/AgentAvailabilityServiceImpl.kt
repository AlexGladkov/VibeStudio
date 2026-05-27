package studio.vibe.shared.service.agent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import studio.vibe.shared.contract.AgentAvailabilityChecking
import studio.vibe.shared.contract.AgentAvailabilityStatus
import studio.vibe.shared.contract.BinaryResolver
import studio.vibe.shared.contract.CredentialStorage
import studio.vibe.shared.model.AIAssistant
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Concrete implementation of [AgentAvailabilityChecking].
 *
 * Checks whether each AI CLI agent's binary exists (via [BinaryResolver]) and whether the
 * required API key is present (via [CredentialStorage]). Results are cached for [CACHE_TTL]
 * to avoid filesystem and keychain thrashing on every UI render cycle.
 *
 * All agents start in [AgentAvailabilityStatus.Checking] state. A background coroutine on
 * the provided [scope] performs the actual checks and updates [availability] atomically.
 *
 * Thread-safety: [availability] and [lastRefreshAt] are only mutated on coroutines launched
 * within [scope]. Callers should ensure [scope] is confined to a single dispatcher (e.g.
 * `Dispatchers.Main`) if UI bindings require main-thread reads.
 *
 * @param binaryResolver Resolves CLI executable names to absolute paths.
 * @param credentialStorage Reads stored API keys (Keychain / DPAPI / libsecret).
 * @param scope The coroutine scope used for background refresh tasks.
 */
class AgentAvailabilityServiceImpl(
    private val binaryResolver: BinaryResolver,
    private val credentialStorage: CredentialStorage,
    private val scope: CoroutineScope,
) : AgentAvailabilityChecking {

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        /** How long a full-refresh result is considered fresh before the next [check] triggers another. */
        val CACHE_TTL = 30.seconds
    }

    // ── State ─────────────────────────────────────────────────────────────────

    // AgentAvailabilityChecking exposes Map<AIAssistant, AgentAvailabilityStatus>.
    // We keep a mutable backing map and publish snapshots as a new immutable map on each update.
    private val _availability: MutableMap<AIAssistant, AgentAvailabilityStatus> =
        AIAssistant.entries.associateWithTo(mutableMapOf()) { AgentAvailabilityStatus.Checking }

    /**
     * Snapshot of the current availability for every known [AIAssistant].
     * Always contains an entry for every value in [AIAssistant.entries].
     */
    override val availability: Map<AIAssistant, AgentAvailabilityStatus>
        get() = _availability.toMap()

    /** Timestamp of the most recent completed (or in-flight) full refresh. */
    private var lastRefreshAt: Instant = Instant.DISTANT_PAST

    // ── AgentAvailabilityChecking ─────────────────────────────────────────────

    /**
     * Trigger an asynchronous refresh of all agent statuses.
     *
     * The timestamp is stamped immediately to prevent concurrent callers from enqueuing
     * duplicate refreshes. The result is written back into [_availability] once complete.
     */
    override fun refreshAll() {
        // Stamp immediately — prevents stacked concurrent refresh calls.
        lastRefreshAt = Clock.System.now()

        scope.launch {
            val results = mutableMapOf<AIAssistant, AgentAvailabilityStatus>()

            for (agent in AIAssistant.entries) {
                val executableName = agent.executableName

                val resolvedPath = if (executableName.isNotEmpty() &&
                    !executableName.contains('/') &&
                    !executableName.contains("..")
                ) {
                    binaryResolver.findExecutable(executableName)?.path
                } else {
                    null
                }

                if (resolvedPath != null) {
                    val hasApiKey = resolveApiKeyAvailability(agent)
                    results[agent] = AgentAvailabilityStatus.Available(
                        path = resolvedPath,
                        hasAPIKey = hasApiKey,
                    )
                } else {
                    results[agent] = AgentAvailabilityStatus.NotInstalled(
                        installHint = agent.installHint,
                    )
                }
            }

            // Write results back — runs on the scope's dispatcher.
            for ((agent, status) in results) {
                _availability[agent] = status
            }
        }
    }

    /**
     * Return the cached availability status for [agent], triggering a background refresh
     * if the cache is stale.
     *
     * Returns [AgentAvailabilityStatus.Checking] on the very first call before the first
     * refresh completes.
     */
    override fun check(agent: AIAssistant): AgentAvailabilityStatus {
        val elapsed = Clock.System.now() - lastRefreshAt
        if (elapsed > CACHE_TTL) {
            refreshAll()
        }
        return _availability[agent] ?: AgentAvailabilityStatus.Checking
    }

    /**
     * Return `true` if the agent binary is found, regardless of whether an API key is set.
     *
     * Agents that require a key (Codex, Gemini, QwenCode) handle the missing-key scenario
     * themselves by prompting interactively inside the terminal session. The "no API key"
     * badge in the picker is informational only and does not block launch.
     */
    override fun canLaunch(agent: AIAssistant): Boolean {
        return check(agent) is AgentAvailabilityStatus.Available
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /**
     * Check whether an API key is available for [agent].
     *
     * Lookup order:
     * 1. [CredentialStorage] (Keychain / DPAPI / libsecret).
     * 2. Returns `true` if the agent does not require an API key ([AIAssistant.apiKeyEnvironmentVariable] == null).
     *
     * Note: Environment variable fallback is intentionally omitted here. The KMP layer
     * does not have a cross-platform `ProcessInfo.environment` equivalent. Platform-specific
     * implementations can extend this service or provide a richer [CredentialStorage] that
     * also reads from the process environment.
     *
     * This function is `suspend` because [CredentialStorage.load] is `suspend`.
     */
    private suspend fun resolveApiKeyAvailability(agent: AIAssistant): Boolean {
        val envVar = agent.apiKeyEnvironmentVariable
            ?: return true  // Agent doesn't need an API key.

        val storedValue = credentialStorage.load(envVar)
        return !storedValue.isNullOrEmpty()
    }
}

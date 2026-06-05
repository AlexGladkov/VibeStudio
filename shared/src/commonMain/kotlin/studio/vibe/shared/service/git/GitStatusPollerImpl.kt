package studio.vibe.shared.service.git

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import studio.vibe.shared.contract.GitServicing
import studio.vibe.shared.contract.GitStatusPolling
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.GitStatus
import kotlin.math.min
import kotlin.math.pow
import kotlin.time.Duration.Companion.seconds

/**
 * Periodically polls git status for a repository with exponential backoff on errors.
 *
 * Polling intervals:
 * - Active project:      3 seconds
 * - Background project:  30 seconds
 * - On error:            exponential backoff: base * 2^min(errors, 4), capped at 30 s
 *
 * [refreshNow] triggers an immediate poll and cancels any previously scheduled
 * refresh so rapid file-save bursts coalesce into a single poll.
 *
 * @param gitService The git service used to fetch status.
 * @param scope Coroutine scope for all polling coroutines. Should be a long-lived
 *   scope tied to the component that owns this poller (e.g. a ViewModel or
 *   Decompose Component scope). Cancel the scope to stop polling permanently.
 */
class GitStatusPollerImpl(
    private val gitService: GitServicing,
    private val scope: CoroutineScope,
) : GitStatusPolling {

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    private val activeInterval = 3.seconds
    private val backgroundInterval = 30.seconds
    private val maxBackoffInterval = 30.seconds

    // -------------------------------------------------------------------------
    // Public StateFlows (GitStatusPolling contract)
    // -------------------------------------------------------------------------

    private val _status = MutableStateFlow(GitStatus.EMPTY)
    override val status: StateFlow<GitStatus> = _status.asStateFlow()

    private val _isPolling = MutableStateFlow(false)
    override val isPolling: StateFlow<Boolean> = _isPolling.asStateFlow()

    private val _lastError = MutableStateFlow<Throwable?>(null)
    override val lastError: StateFlow<Throwable?> = _lastError.asStateFlow()

    // -------------------------------------------------------------------------
    // Private state
    // -------------------------------------------------------------------------

    private var pollingJob: Job? = null
    private var refreshJob: Job? = null
    private var currentRepository: FilePath? = null
    private var consecutiveErrors = 0
    private var currentIsActive = true

    /**
     * Serializes [poll] invocations.  The scheduled poll loop and `refreshNow`
     * trigger overlapping polls during rapid file saves; without this lock the
     * `_isPolling` check-then-set window allowed two concurrent git invocations
     * to race on [_status] and [_lastError].
     */
    private val pollMutex = Mutex()

    // -------------------------------------------------------------------------
    // GitStatusPolling API
    // -------------------------------------------------------------------------

    /**
     * Start polling for the given [repository].
     *
     * Cancels any existing polling job and starts a new one immediately.
     *
     * @param repository Root path of the git repository to watch.
     * @param isActive True if this is the foreground / active project
     *   (uses shorter [activeInterval]); false for background projects.
     */
    override fun startPolling(repository: FilePath, isActive: Boolean) {
        stopPolling()
        currentRepository = repository
        currentIsActive = isActive
        consecutiveErrors = 0

        pollingJob = scope.launch {
            // Use currentCoroutineContext().isActive to avoid shadowing by the
            // `isActive: Boolean` parameter — the old `while (isActive)` always
            // evaluated the parameter, not the coroutine cancellation flag.
            while (currentCoroutineContext().isActive) {
                poll()
                val interval = effectiveInterval(currentIsActive)
                delay(interval)
            }
        }
    }

    /** Stop polling. The StateFlows retain their last values. */
    override fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /**
     * Trigger an immediate refresh.
     *
     * Cancels any pending refresh job so rapid calls coalesce into a single poll.
     */
    override fun refreshNow() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            poll()
        }
    }

    // -------------------------------------------------------------------------
    // Private: poll cycle
    // -------------------------------------------------------------------------

    private suspend fun poll() {
        val repository = currentRepository ?: return
        pollMutex.withLock {
            _isPolling.value = true
            try {
                val newStatus = gitService.status(at = repository)

                // Augment ahead/behind from the dedicated command when status shows 0/0,
                // as porcelain output sometimes omits tracking counts.
                var ahead = newStatus.aheadCount
                var behind = newStatus.behindCount
                if (newStatus.branch.isNotEmpty() && ahead == 0 && behind == 0) {
                    try {
                        val counts = gitService.aheadBehind(at = repository)
                        ahead = counts.first
                        behind = counts.second
                    } catch (_: Exception) { /* Non-fatal — no upstream configured */ }
                }

                _status.value = GitStatus(
                    branch = newStatus.branch,
                    aheadCount = ahead,
                    behindCount = behind,
                    stagedFiles = newStatus.stagedFiles,
                    unstagedFiles = newStatus.unstagedFiles,
                    untrackedFiles = newStatus.untrackedFiles,
                )
                _lastError.value = null
                consecutiveErrors = 0
            } catch (e: Exception) {
                _lastError.value = e
                consecutiveErrors++
            } finally {
                _isPolling.value = false
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private: backoff calculation
    // -------------------------------------------------------------------------

    /**
     * Calculate the effective polling interval, applying exponential backoff
     * when there are consecutive errors.
     *
     * Formula: `base * 2^min(errors, 4)`, capped at [maxBackoffInterval].
     */
    private fun effectiveInterval(isActive: Boolean): kotlin.time.Duration {
        val base = if (isActive) activeInterval else backgroundInterval
        return if (consecutiveErrors > 0) {
            val multiplier = 2.0.pow(min(consecutiveErrors, 4).toDouble())
            val backoff = base * multiplier
            if (backoff > maxBackoffInterval) maxBackoffInterval else backoff
        } else {
            base
        }
    }
}

@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlinx.coroutines.FlowPreview::class)

package studio.vibe.desktop.terminal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import studio.vibe.shared.contract.SessionPersisting
import kotlin.uuid.Uuid

/**
 * Coordinates debounced persistence of PTY scrollback buffers.
 *
 * [receiveScrollbackDirty] is called on every PTY output chunk (hot path).
 * Internally a per-session [MutableStateFlow] de-duplicates rapid changes,
 * and a [debounce] of [DEBOUNCE_MS] ensures disk writes happen at most once
 * per 2 seconds of inactivity per session.
 *
 * @param sessionPersistence Storage back-end for scrollback content.
 * @param terminalService    Source of live scrollback content.
 * @param scope              Coroutine scope in which debounce collectors run.
 */
class ScrollbackPersistCoordinator(
    private val sessionPersistence: SessionPersisting,
    private val terminalService: DesktopTerminalService,
    private val scope: CoroutineScope,
) {
    /** One-entry CONFLATED channel per session — last dirty signal wins. */
    private val dirtySignals = java.util.concurrent.ConcurrentHashMap<Uuid, MutableStateFlow<Uuid?>>()
    private val jobs = java.util.concurrent.ConcurrentHashMap<Uuid, Job>()

    companion object {
        internal const val DEBOUNCE_MS = 2_000L
    }

    /**
     * Signal that [sessionId]'s scrollback has new content.
     *
     * Call this from the PTY output hot path.  This method is non-blocking and
     * safe to call from any thread.
     */
    fun receiveScrollbackDirty(sessionId: Uuid) {
        val flow = dirtySignals.getOrPut(sessionId) {
            val f = MutableStateFlow<Uuid?>(null)
            // Launch a debounced collector for this session the first time it is seen.
            val job = scope.launch(Dispatchers.IO) {
                f.filterNotNull()
                    .debounce(DEBOUNCE_MS)
                    .collect { id ->
                        flushScrollback(id)
                    }
            }
            jobs[sessionId] = job
            f
        }
        flow.value = sessionId
    }

    /**
     * Cancel the debounce job for [sessionId] and perform one final flush.
     * Call this when a session ends.
     */
    suspend fun flushAndCancel(sessionId: Uuid) {
        jobs.remove(sessionId)?.cancel()
        dirtySignals.remove(sessionId)
        flushScrollback(sessionId)
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private suspend fun flushScrollback(sessionId: Uuid) {
        val content = terminalService.scrollbackContent(sessionId) ?: return
        try {
            sessionPersistence.saveScrollback(content, sessionId)
        } catch (e: Exception) {
            println("ScrollbackPersistCoordinator: failed to persist scrollback for $sessionId: ${e.message}")
        }
    }
}

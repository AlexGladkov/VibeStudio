@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.terminal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import studio.vibe.shared.core.common.terminal.TerminalSession
import kotlin.uuid.Uuid

/**
 * Thread-safe registry of live [PtySessionState] entries.
 *
 * Owns [_sessions] and all mutations to it: add, remove (force-cancel scope),
 * unregister (natural exit — scope completes on its own), and model updates.
 *
 * All public state is derived atomically from [_sessions] via [stateIn].
 */
internal class PtySessionRegistry(serviceScope: CoroutineScope) {

    private val _sessions: MutableStateFlow<Map<Uuid, PtySessionState>> =
        MutableStateFlow(emptyMap())

    /** Snapshot of [_sessions] for internal callers that need the full state. */
    val snapshot: Map<Uuid, PtySessionState> get() = _sessions.value

    /** Project → list of [TerminalSession] derived atomically from [_sessions]. */
    val sessionsByProject: StateFlow<Map<Uuid, List<TerminalSession>>> =
        _sessions.map { map ->
            map.values
                .groupBy { it.session.projectId }
                .mapValues { (_, states) -> states.map { it.session } }
        }.stateIn(serviceScope, SharingStarted.Eagerly, emptyMap())

    /** Adds a new session to the registry. */
    fun add(state: PtySessionState) {
        _sessions.update { it + (state.session.id to state) }
    }

    /**
     * Removes a session from the registry, closes its stdin writer, and cancels
     * its [CoroutineScope].
     *
     * Use for external kills ([DesktopTerminalService.killSession]): the PTY
     * process has been killed, but the `sessionScope` watchProcessExit coroutine
     * may still be suspended inside `waitFor`. Without explicit cancellation it
     * would survive for the entire [serviceScope] lifetime and leak memory.
     */
    fun remove(sessionId: Uuid) {
        val removed: PtySessionState? = run {
            var captured: PtySessionState? = null
            _sessions.update { current ->
                captured = current[sessionId]
                if (captured != null) current - sessionId else current
            }
            captured
        }
        if (removed != null) {
            removed.graceKillJob?.cancel()
            removed.graceKillJob = null
            runCatching { removed.inputWriter.close() }
            removed.scope.cancel()
        }
    }

    /**
     * Removes a session from the registry **without** cancelling its scope.
     *
     * Used by [PtyOutputWatcher.watchProcessExit] after natural process exit,
     * where the enclosing `sessionScope.launch { ... }` completes on its own.
     */
    fun unregister(sessionId: Uuid) {
        _sessions.update { it - sessionId }
    }

    /**
     * Replaces a session model in the registry and refreshes [sessionsByProject].
     *
     * Returns a new map copy so [StateFlow] detects the change and re-emits,
     * which triggers the derived [sessionsByProject] stateIn to update.
     */
    fun updateModel(updated: TerminalSession) {
        _sessions.update { map ->
            val state = map[updated.id] ?: return@update map
            state.session = updated
            map.toMutableMap()
        }
    }

    /**
     * Forces a re-emission of [_sessions] without mutating state.
     *
     * Used after [PtySessionState.session] is mutated in-place (e.g. on exit)
     * so that [sessionsByProject] sees the updated value.
     */
    fun touch() {
        _sessions.update { it }
    }

    /** Returns the raw pty4j process for [sessionId], or `null` if unknown. */
    fun ptyProcess(sessionId: Uuid): com.pty4j.PtyProcess? =
        _sessions.value[sessionId]?.ptyProcess

    /** Returns the full internal state snapshot (for JediTerm wiring in [TerminalView]). */
    fun internalSessions(): Map<Uuid, PtySessionState> = _sessions.value

    /** Returns the [PtySessionState] for [sessionId], or `null` if unknown. */
    operator fun get(sessionId: Uuid): PtySessionState? = _sessions.value[sessionId]
}

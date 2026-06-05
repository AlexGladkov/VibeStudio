@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import studio.vibe.shared.contract.TerminalSessionEvent
import studio.vibe.shared.model.TabActivityState
import studio.vibe.shared.model.TerminalSessionState
import kotlin.uuid.Uuid

/**
 * Handles PTY output capture, activity signalling, and process-exit watching.
 *
 * Extracted from [DesktopTerminalService] to keep a single responsibility: reacting
 * to bytes flowing out of a PTY process and coordinating lifecycle transitions.
 *
 * @param registry The session registry — used to update session state on exit and
 *   to call [PtySessionRegistry.unregister] after natural process completion.
 */
internal class PtyOutputWatcher(private val registry: PtySessionRegistry) {

    private val _projectActivityStates =
        MutableStateFlow<Map<Uuid, TabActivityState>>(emptyMap())
    val projectActivityStates: StateFlow<Map<Uuid, TabActivityState>> =
        _projectActivityStates

    private val _sessionEvents = MutableSharedFlow<TerminalSessionEvent>(
        extraBufferCapacity = 64,
    )
    val sessionEvents: Flow<TerminalSessionEvent> = _sessionEvents.asSharedFlow()

    /**
     * Receives a chunk of terminal output captured by [Pty4JConnector.read].
     *
     * Single entry point for PTY output into the service. After JediTerm's
     * [ProcessTtyConnector] reads the bytes, it forwards each chunk here for:
     *  1. [PtySessionState.outputFlow] emission (subscribers, Remote Control).
     *  2. [PtySessionState.scrollback] accumulation (capped at 100 KB).
     *  3. Project activity signalling.
     */
    fun receiveOutput(sessionId: Uuid, chunk: String) {
        val state = registry[sessionId] ?: return
        state.outputFlow.tryEmit(chunk)
        state.scrollback.append(chunk)
        markActivity(sessionId, state.session.projectId)
    }

    /**
     * Waits for the PTY process to exit, emits [TerminalSessionEvent.ProcessExited],
     * updates the session state to [TerminalSessionState.Exited], and unregisters
     * the session after an optional visibility delay.
     *
     * Runs on [Dispatchers.IO] — the caller must launch this in the session scope.
     * Does NOT cancel the session scope; the enclosing `launch { }` completes
     * naturally after this function returns.
     */
    suspend fun watchProcessExit(
        sessionId: Uuid,
        projectId: Uuid,
        state: PtySessionState,
    ) {
        withContext(Dispatchers.IO) {
            val exitCode = runCatching { state.ptyProcess.waitFor() }.getOrDefault(-1)
            val exited = state.session.copy(state = TerminalSessionState.Exited(exitCode))
            state.session = exited
            // Touch the registry to trigger re-emission of sessionsByProject.
            registry.touch()
            _sessionEvents.tryEmit(
                TerminalSessionEvent.ProcessExited(
                    sessionId = sessionId,
                    projectId = projectId,
                    exitCode = exitCode,
                ),
            )

            if (state.session.isAgentSession) {
                // Keep agent session visible for 10 s so the user can read error output.
                kotlinx.coroutines.delay(10_000)
            }

            // Unregister without cancelling scope — we are inside the session scope;
            // calling remove() would self-cancel and throw a spurious CancellationException.
            registry.unregister(sessionId)
            runCatching { state.inputWriter.close() }
        }
    }

    /** Marks the owning project as having terminal activity. */
    fun markActivity(sessionId: Uuid, projectId: Uuid) {
        _projectActivityStates.update { current ->
            current + (projectId to TabActivityState.RUNNING)
        }
        _sessionEvents.tryEmit(
            TerminalSessionEvent.ActivityDetected(
                sessionId = sessionId,
                projectId = projectId,
            ),
        )
    }

    /** Clears the RUNNING activity state for [projectId] (marking it as seen). */
    fun clearActivity(projectId: Uuid) {
        _projectActivityStates.update { current ->
            current.toMutableMap().apply {
                if (this[projectId] == TabActivityState.RUNNING) {
                    this[projectId] = TabActivityState.IDLE
                }
            }
        }
    }
}

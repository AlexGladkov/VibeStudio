@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.terminal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * Encapsulates PTY process kill strategies: immediate force-kill and graceful
 * SIGTERM-then-SIGKILL with a 2-second grace period.
 *
 * Extracted from [DesktopTerminalService] to keep kill logic in one place and
 * make it independently testable.
 *
 * @param registry The session registry used to look up and remove sessions.
 * @param serviceScope Scope for launching the grace-kill background timer.
 */
internal class PtyKillStrategy(
    private val registry: PtySessionRegistry,
    private val serviceScope: CoroutineScope,
) {

    /**
     * Kills [sessionId].
     *
     * When [force] is `true` the process is SIGKILLed immediately and the session
     * is removed synchronously. When [force] is `false`, SIGTERM is sent first and
     * the session is removed after a 2-second grace period (or immediately once
     * the process has already died), matching Swift TerminalService behaviour.
     */
    fun kill(sessionId: Uuid, force: Boolean) {
        val state = registry[sessionId] ?: return
        if (force) {
            runCatching { state.ptyProcess.destroyForcibly() }
            registry.remove(sessionId)
        } else {
            runCatching { state.ptyProcess.destroy() }  // sends SIGTERM
            // Grace period: wait 2 seconds, then SIGKILL if still alive.
            // Stored in PtySessionState so remove() can cancel it and
            // prevent double-execution if the session is also killed forcibly.
            state.graceKillJob = serviceScope.launch(Dispatchers.IO) {
                kotlinx.coroutines.delay(2_000)
                if (state.ptyProcess.isAlive) {
                    runCatching { state.ptyProcess.destroyForcibly() }
                }
                registry.remove(sessionId)
            }
        }
    }

    /** Kills all sessions belonging to [projectId] with force. */
    fun killAll(projectId: Uuid) {
        registry.snapshot.entries
            .filter { (_, s) -> s.session.projectId == projectId }
            .forEach { (id, _) -> kill(id, force = true) }
    }

    /** Kills every registered session with force. Used during [DesktopTerminalService.dispose]. */
    fun killAllSessions() {
        registry.snapshot.keys.toList().forEach { id -> kill(id, force = true) }
    }
}

package studio.vibe.shared.usecase

import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.contract.TerminalSessionManaging
import kotlin.uuid.ExperimentalUuidApi

/**
 * Activates the first available project and opens a terminal session for it.
 *
 * Called when no previous session exists (first launch) or as a fallback when the
 * restored snapshot did not produce an active project selection. The use case is a
 * no-op when [ProjectManaging.activeProjectId] is already set or when the project
 * list is empty.
 */
@OptIn(ExperimentalUuidApi::class)
class ActivateFirstProjectUseCase(
    private val projectManager: ProjectManaging,
    private val terminalManager: TerminalSessionManaging,
) {
    /**
     * Activate the first project if no project is currently active.
     *
     * Terminal session creation errors are silently discarded — the project is still
     * activated even when the PTY cannot be spawned immediately.
     */
    fun execute() {
        if (projectManager.activeProjectId.value != null) return
        val first = projectManager.projects.value.firstOrNull() ?: return

        projectManager.setActiveProjectId(first.id)

        // Guard: RestoreSessionUseCase may have already created a terminal for this
        // project. This happens when the snapshot's activeProjectId was a FreeTab
        // UUID (not persisted across restarts), leaving activeProjectId unset while
        // still restoring sessions for all real projects. Creating another session
        // here would produce an unwanted split with two duplicate terminals.
        if (terminalManager.sessions(first.id).isNotEmpty()) return

        try {
            terminalManager.createSession(
                projectId = first.id,
                shell = first.shellPath,
                workingDirectory = first.path,
            )
        } catch (e: Exception) {
            // Silently discard — project is still activated.
            println("ActivateFirstProjectUseCase: failed to create terminal session: ${e.message}")
        }
    }
}

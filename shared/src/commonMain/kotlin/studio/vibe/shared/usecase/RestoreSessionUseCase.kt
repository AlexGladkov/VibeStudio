package studio.vibe.shared.usecase

import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.contract.SessionPersisting
import studio.vibe.shared.contract.TerminalSessionManaging
import kotlin.uuid.ExperimentalUuidApi

/**
 * Restores the previous application session from disk.
 *
 * Extracts session-restore business logic into a focused, testable class.
 * Call [execute] once during application startup before the first frame is rendered.
 */
@OptIn(ExperimentalUuidApi::class)
class RestoreSessionUseCase(
    private val projectManager: ProjectManaging,
    private val terminalManager: TerminalSessionManaging,
    private val sessionPersistence: SessionPersisting,
) {
    /**
     * Restore the previously persisted application session.
     *
     * @return `true` if a snapshot was found and applied; `false` when no snapshot
     *   exists (first launch or after a reset). Errors during decode or terminal
     *   creation are logged but do not propagate.
     */
    suspend fun execute(): Boolean {
        return try {
            val snapshot = sessionPersistence.restore() ?: return false

            // Restore active project selection.
            snapshot.activeProjectId?.let { activeId ->
                if (projectManager.project(activeId) != null) {
                    projectManager.setActiveProjectId(activeId)
                }
            }

            // Restore terminal sessions for each project.
            for (projectSession in snapshot.projectSessions) {
                val project = projectManager.project(projectSession.projectId) ?: continue

                // Restore at most one terminal per project.
                // Multiple saved layouts can occur if the user had a split open —
                // restoring all of them silently creates duplicate windows on startup.
                for (layout in projectSession.terminalLayouts.take(1)) {
                    try {
                        terminalManager.createSession(
                            projectId = project.id,
                            shell = project.shellPath,
                            workingDirectory = layout.workingDirectory ?: project.path,
                        )
                    } catch (e: Exception) {
                        println("RestoreSessionUseCase: failed to restore terminal session: ${e.message}")
                    }
                }
            }
            true
        } catch (e: Exception) {
            println("RestoreSessionUseCase: failed to restore session: ${e.message}")
            false
        }
    }
}

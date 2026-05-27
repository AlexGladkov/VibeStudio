package studio.vibe.shared.usecase

import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.contract.SessionPersisting
import studio.vibe.shared.contract.TerminalSessionManaging
import studio.vibe.shared.model.AppSessionSnapshot
import studio.vibe.shared.model.ProjectSessionSnapshot
import studio.vibe.shared.model.TerminalLayoutSnapshot
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Captures and persists the current application session state.
 *
 * Extracts session-saving business logic into a focused, testable class.
 * Call [execute] at application termination or during periodic auto-save.
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
class SaveSessionUseCase(
    private val projectManager: ProjectManaging,
    private val terminalManager: TerminalSessionManaging,
    private val sessionPersistence: SessionPersisting,
) {
    /**
     * Capture current application state and write it to persistent storage.
     *
     * Errors from [SessionPersisting.save] are logged but not re-thrown so that
     * callers (application termination handlers) are not blocked.
     */
    suspend fun execute() {
        val projectSessions = projectManager.projects.value.map { project ->
            val sessions = terminalManager.sessions(project.id)
            val layouts = sessions.map { session ->
                TerminalLayoutSnapshot(
                    sessionId = session.id,
                    title = session.title,
                    splitDirection = session.splitDirection,
                    workingDirectory = project.path,
                )
            }
            ProjectSessionSnapshot(
                projectId = project.id,
                terminalLayouts = layouts,
                scrollbackFile = null,
                sidebarVisible = true,
                sidebarWidth = 220.0,
            )
        }

        // Resolve the active ID: only persist it when it belongs to a real project.
        // If a FreeTab was active at quit, its UUID is not a real project ID and
        // would not survive the next RestoreSessionUseCase lookup — leaving
        // activeProjectId unresolved on startup and causing ActivateFirstProjectUseCase
        // to create an extra session on top of the already-restored ones.
        val resolvedActiveId: Uuid? = projectManager.activeProjectId.value?.let { id ->
            if (projectManager.project(id) != null) id else null
        }

        val snapshot = AppSessionSnapshot(
            version = sessionPersistence.currentSnapshotVersion,
            capturedAt = Clock.System.now(),
            activeProjectId = resolvedActiveId,
            projectSessions = projectSessions,
        )

        try {
            sessionPersistence.save(snapshot)
        } catch (e: Exception) {
            // Log errors but do not propagate — callers must not be blocked.
            println("SaveSessionUseCase: failed to save session: ${e.message}")
        }
    }
}

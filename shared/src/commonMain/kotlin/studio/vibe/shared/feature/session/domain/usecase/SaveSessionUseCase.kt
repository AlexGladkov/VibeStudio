package studio.vibe.shared.feature.session.domain.usecase

import studio.vibe.shared.core.common.NoParamsUseCase
import studio.vibe.shared.core.common.project.ProjectManaging
import studio.vibe.shared.feature.session.domain.contract.SessionPersisting
import studio.vibe.shared.core.common.terminal.TerminalSessionManaging
import studio.vibe.shared.feature.session.domain.model.AppSessionSnapshot
import studio.vibe.shared.core.common.project.ProjectSessionSnapshot
import studio.vibe.shared.core.common.terminal.TerminalLayoutSnapshot
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Captures and persists the current application session state.
 *
 * Extracts session-saving business logic into a focused, testable class.
 * Call [invoke] at application termination or during periodic auto-save.
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
class SaveSessionUseCase(
    private val projectManager: ProjectManaging,
    private val terminalManager: TerminalSessionManaging,
    private val sessionPersistence: SessionPersisting,
) : NoParamsUseCase<Unit> {

    override suspend operator fun invoke(): Result<Unit> = runCatching {
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
        val resolvedActiveId: Uuid? = projectManager.activeProjectId.value?.let { id ->
            if (projectManager.project(id) != null) id else null
        }

        val snapshot = AppSessionSnapshot(
            version = sessionPersistence.currentSnapshotVersion,
            capturedAt = Clock.System.now(),
            activeProjectId = resolvedActiveId,
            projectSessions = projectSessions,
        )

        sessionPersistence.save(snapshot)
    }
}

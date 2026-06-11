package studio.vibe.shared.feature.session.domain.usecase

import studio.vibe.shared.core.common.NoParamsUseCase
import studio.vibe.shared.core.common.project.ProjectManaging
import studio.vibe.shared.feature.session.domain.contract.SessionPersisting
import studio.vibe.shared.core.common.terminal.TerminalSessionManaging
import kotlin.uuid.ExperimentalUuidApi

/**
 * Restores the previous application session from disk.
 *
 * Extracts session-restore business logic into a focused, testable class.
 * Call [invoke] once during application startup before the first frame is rendered.
 */
@OptIn(ExperimentalUuidApi::class)
class RestoreSessionUseCase(
    private val projectManager: ProjectManaging,
    private val terminalManager: TerminalSessionManaging,
    private val sessionPersistence: SessionPersisting,
) : NoParamsUseCase<Boolean> {

    /**
     * Restore the previously persisted application session.
     *
     * @return [Result.success] with `true` if a snapshot was found and applied;
     *   [Result.success] with `false` when no snapshot exists (first launch or after a reset).
     *   Errors during decode are wrapped in [Result.failure].
     */
    override suspend operator fun invoke(): Result<Boolean> = runCatching {
        val snapshot = sessionPersistence.restore() ?: return@runCatching false

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
            for (layout in projectSession.terminalLayouts.take(1)) {
                runCatching {
                    terminalManager.createSession(
                        projectId = project.id,
                        shell = project.shellPath,
                        workingDirectory = layout.workingDirectory ?: project.path,
                    )
                }
            }
        }
        true
    }
}

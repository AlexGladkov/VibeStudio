package studio.vibe.shared.feature.project.domain.usecase

import studio.vibe.shared.core.common.NoParamsUseCase
import studio.vibe.shared.core.common.project.ProjectManaging
import studio.vibe.shared.core.common.terminal.TerminalSessionManaging
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
) : NoParamsUseCase<Unit> {

    override suspend operator fun invoke(): Result<Unit> = runCatching {
        if (projectManager.activeProjectId.value != null) return@runCatching
        val first = projectManager.projects.value.firstOrNull() ?: return@runCatching

        projectManager.setActiveProjectId(first.id)

        // Guard: RestoreSessionUseCase may have already created a terminal for this
        // project. This happens when the snapshot's activeProjectId was a FreeTab
        // UUID (not persisted across restarts), leaving activeProjectId unset while
        // still restoring sessions for all real projects. Creating another session
        // here would produce an unwanted split with two duplicate terminals.
        if (terminalManager.sessions(first.id).isNotEmpty()) return@runCatching

        terminalManager.createSession(
            projectId = first.id,
            shell = first.shellPath,
            workingDirectory = first.path,
        )
    }
}

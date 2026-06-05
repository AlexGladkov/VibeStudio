@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import studio.vibe.shared.contract.CodeSpeakServicing
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.coordinator.AppNavigationCoordinator
import studio.vibe.shared.usecase.RestoreSessionUseCase

/**
 * Coordinates the application startup sequence and drives the ongoing
 * "active project → mode sync" observation loop.
 *
 * Mirrors Swift `AppLifecycleCoordinator` — keeping Main.kt as a thin shell
 * that only wires the window and hands off to this coordinator.
 *
 * Startup order:
 *  1. [RestoreSessionUseCase.execute] — restore previous terminal sessions.
 *  2. Auto-activate the first project if no session was restored.
 *  3. Begin collecting [ProjectManaging.activeProjectId] → checkConfig → syncMode.
 *
 * Shutdown: call [onShutdown] from the window close handler before [DesktopServiceContainer.dispose].
 */
class AppLifecycleCoordinator(
    private val projectManaging: ProjectManaging,
    private val codeSpeakService: CodeSpeakServicing,
    private val navigationCoordinator: AppNavigationCoordinator,
    private val restoreSessionUseCase: RestoreSessionUseCase,
    private val scope: CoroutineScope,
) {

    /**
     * Must be called once after the service container is ready.
     * Safe to call from a non-main coroutine (uses [Dispatchers.IO] internally).
     */
    fun onStartup() {
        // Phase 1: restore sessions and auto-activate the first project.
        scope.launch(Dispatchers.IO) {
            restoreSessionUseCase.execute()

            val projects = projectManaging.projects.value
            if (projectManaging.activeProjectId.value == null && projects.isNotEmpty()) {
                projectManaging.setActiveProjectId(projects.first().id)
            }
        }

        // Phase 2: keep AppMode in sync with the active project's CS config.
        scope.launch(Dispatchers.IO) {
            projectManaging.activeProjectId.collectLatest { activeId ->
                val project = activeId?.let { projectManaging.project(it) }
                if (project != null) {
                    codeSpeakService.checkConfig(project)
                }
                val isCS = activeId?.let { codeSpeakService.isCodeSpeakProject(it) } ?: false
                navigationCoordinator.syncMode(isCS)
            }
        }
    }

    /**
     * Called just before the window closes.
     *
     * Currently a no-op (teardown is handled by [DesktopServiceContainer.dispose]),
     * but provides a stable hook for future pre-dispose logic (analytics flush,
     * auto-save, etc.) without touching Main.kt.
     */
    fun onShutdown() = Unit
}

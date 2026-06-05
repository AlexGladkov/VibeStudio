@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import studio.vibe.shared.contract.CodeSpeakServicing
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.coordinator.AppNavigationCoordinator
import studio.vibe.shared.preferences.RemoteControlPreferences
import studio.vibe.shared.usecase.RestoreSessionUseCase
import studio.vibe.desktop.remote.RemoteControlServer

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
 *  3. Auto-start [RemoteControlServer] if [RemoteControlPreferences.remoteControlEnabled].
 *  4. Begin collecting [ProjectManaging.activeProjectId] → checkConfig → syncMode.
 *
 * Shutdown: call [onShutdown] from the window close handler before [DesktopServiceContainer.dispose].
 */
class AppLifecycleCoordinator(
    private val projectManaging: ProjectManaging,
    private val codeSpeakService: CodeSpeakServicing,
    private val navigationCoordinator: AppNavigationCoordinator,
    private val restoreSessionUseCase: RestoreSessionUseCase,
    private val remoteControlPreferences: RemoteControlPreferences,
    private val remoteControlServer: RemoteControlServer,
    private val scope: CoroutineScope,
) {

    /**
     * Must be called once after the service container is ready.
     *
     * Phase 1 and Phase 2 run sequentially in a single coroutine: Phase 2 (the
     * collect loop) only begins after Phase 1 (session restore + auto-activate)
     * completes. This guarantees that [ProjectManaging.activeProjectId] is
     * already set when the CS-config sync loop starts its first iteration.
     */
    fun onStartup() {
        scope.launch(Dispatchers.IO) {
            // Phase 1: restore sessions and auto-activate the first project.
            restoreSessionUseCase.execute()

            val projects = projectManaging.projects.value
            if (projectManaging.activeProjectId.value == null && projects.isNotEmpty()) {
                projectManaging.setActiveProjectId(projects.first().id)
            }

            // Phase 1b: auto-start Remote Control server if the user previously
            // enabled it — mirrors Swift AppLifecycleCoordinator lines 148-150.
            // Default is false (opt-in), so this is a no-op on first launch.
            if (remoteControlPreferences.remoteControlEnabled) {
                remoteControlServer.startAsync()
            }

            // Phase 2: keep AppMode in sync with the active project's CS config.
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

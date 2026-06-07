@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlinx.coroutines.FlowPreview::class)

package studio.vibe.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import studio.vibe.shared.contract.FreeTabManaging
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.contract.SessionPersisting
import studio.vibe.shared.contract.TerminalSessionManaging
import studio.vibe.shared.usecase.AssistantLauncher
import studio.vibe.shared.usecase.SaveSessionUseCase

/**
 * Drives periodic and event-triggered session autosave.
 *
 * Listens on:
 * - [FreeTabManaging.freeTabsFlow] — free tabs changed
 * - [ProjectManaging.projects] — project list changed
 * - [AssistantLauncher.runningByProject] — running-agent map changed
 *
 * Each flow is debounced by [DEBOUNCE_MS] to avoid thrashing disk on rapid changes.
 *
 * A safety-net timer fires every [SAFETY_INTERVAL_MS] regardless of flow activity,
 * ensuring the session is saved even when no state transitions occur.
 *
 * Call [start] once after the service container is ready.
 * Call [stop] before [dispose] to cancel all background jobs.
 */
class SessionAutosaveCoordinator(
    private val projectManaging: ProjectManaging,
    private val terminalManager: TerminalSessionManaging,
    private val sessionPersistence: SessionPersisting,
    private val freeTabManaging: FreeTabManaging,
    private val assistantLauncher: AssistantLauncher,
    private val scope: CoroutineScope,
) {
    private val saveUseCase = SaveSessionUseCase(
        projectManager = projectManaging,
        terminalManager = terminalManager,
        sessionPersistence = sessionPersistence,
    )

    private val jobs = mutableListOf<Job>()

    companion object {
        /** Delay (ms) after the last emission before triggering a save. */
        internal const val DEBOUNCE_MS = 2_000L

        /** Period (ms) of the safety-net save timer. */
        internal const val SAFETY_INTERVAL_MS = 30_000L
    }

    /**
     * Start all background autosave collectors.
     * Idempotent: subsequent calls are no-ops if already started.
     */
    fun start() {
        if (jobs.isNotEmpty()) return

        // Free-tab changes
        jobs += scope.launch(Dispatchers.IO) {
            freeTabManaging.freeTabsFlow
                .debounce(DEBOUNCE_MS)
                .collect { save("free-tabs changed") }
        }

        // Project list changes
        jobs += scope.launch(Dispatchers.IO) {
            projectManaging.projects
                .debounce(DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { save("projects changed") }
        }

        // Running-agent map changes
        jobs += scope.launch(Dispatchers.IO) {
            assistantLauncher.runningByProject
                .debounce(DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { save("running agents changed") }
        }

        // 30-second safety-net
        jobs += scope.launch(Dispatchers.IO) {
            while (true) {
                delay(SAFETY_INTERVAL_MS)
                save("periodic safety-net")
            }
        }
    }

    /**
     * Cancel all background autosave jobs.
     */
    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    private suspend fun save(reason: String) {
        try {
            saveUseCase.execute()
        } catch (e: Exception) {
            println("SessionAutosaveCoordinator: save failed ($reason): ${e.message}")
        }
    }
}

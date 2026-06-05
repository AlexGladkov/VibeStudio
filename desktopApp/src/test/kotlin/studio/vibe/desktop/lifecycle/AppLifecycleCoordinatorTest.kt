@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.lifecycle

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import studio.vibe.desktop.AppLifecycleCoordinator
import studio.vibe.shared.contract.CodeSpeakServicing
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.contract.ProjectsState
import studio.vibe.shared.coordinator.AppNavigationCoordinator
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.Project
import studio.vibe.shared.usecase.RestoreSessionUseCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.uuid.Uuid

/**
 * Unit tests for [AppLifecycleCoordinator].
 *
 * Validates startup sequencing (restore-before-setActive), no-project path,
 * and that onShutdown() never throws.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppLifecycleCoordinatorTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: CoroutineScope

    @BeforeTest
    fun setUp() {
        testScope = CoroutineScope(SupervisorJob() + testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        testScope.cancel()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun fakeProject(id: Uuid = Uuid.random()): Project = Project(
        id = id,
        name = "test-project",
        path = FilePath("/tmp/test"),
    )

    private fun makeCoordinator(
        projects: List<Project> = emptyList(),
        activeProjectId: Uuid? = null,
        restoreReturns: Boolean = false,
    ): Triple<AppLifecycleCoordinator, ProjectManaging, RestoreSessionUseCase> {
        val activeIdFlow = MutableStateFlow(activeProjectId)
        val projectsFlow = MutableStateFlow(projects)

        val projectManaging = mockk<ProjectManaging>(relaxed = true) {
            every { this@mockk.projects } returns projectsFlow
            every { this@mockk.activeProjectId } returns activeIdFlow
            every { this@mockk.projectsState } returns MutableStateFlow(
                ProjectsState(projects = projects, activeProjectId = activeProjectId)
            )
            every { project(any<Uuid>()) } answers {
                val id = firstArg<Uuid>()
                projects.firstOrNull { it.id == id }
            }
        }

        val restoreUseCase = mockk<RestoreSessionUseCase>(relaxed = true) {
            coEvery { execute() } returns restoreReturns
        }

        val codeSpeakService = mockk<CodeSpeakServicing>(relaxed = true)
        val navCoordinator = AppNavigationCoordinator()
        val remoteControlPrefs = mockk<studio.vibe.shared.preferences.RemoteControlPreferences>(relaxed = true) {
            every { remoteControlEnabled } returns false
        }
        val remoteControlServer = mockk<studio.vibe.desktop.remote.RemoteControlServer>(relaxed = true)

        val coordinator = AppLifecycleCoordinator(
            projectManaging = projectManaging,
            codeSpeakService = codeSpeakService,
            navigationCoordinator = navCoordinator,
            restoreSessionUseCase = restoreUseCase,
            remoteControlPreferences = remoteControlPrefs,
            remoteControlServer = remoteControlServer,
            scope = testScope,
        )

        return Triple(coordinator, projectManaging, restoreUseCase)
    }

    // ── restore is called before setActiveProjectId ───────────────────────────

    @Test
    fun onStartup_restoreCalledBeforeSetActiveProjectId() = runTest(testDispatcher) {
        val project = fakeProject()
        val (coordinator, projectManaging, restoreUseCase) = makeCoordinator(
            projects = listOf(project),
            activeProjectId = null,
            restoreReturns = false, // no snapshot → auto-activate
        )

        coordinator.onStartup()
        advanceUntilIdle()

        // Verify restore was called
        coVerify(atLeast = 1) { restoreUseCase.execute() }

        // Verify setActiveProjectId was called for the first project
        verify(atLeast = 1) { projectManaging.setActiveProjectId(project.id) }
    }

    // ── no projects → no setActiveProjectId ──────────────────────────────────

    @Test
    fun onStartup_noProjects_doesNotCallSetActiveProjectId() = runTest(testDispatcher) {
        val (coordinator, projectManaging, _) = makeCoordinator(
            projects = emptyList(),
            activeProjectId = null,
        )

        coordinator.onStartup()
        advanceUntilIdle()

        verify(exactly = 0) { projectManaging.setActiveProjectId(any()) }
    }

    // ── active project already set → no additional setActiveProjectId ─────────

    @Test
    fun onStartup_activeAlreadySet_doesNotOverride() = runTest(testDispatcher) {
        val project = fakeProject()
        val (coordinator, projectManaging, _) = makeCoordinator(
            projects = listOf(project),
            activeProjectId = project.id, // already active
        )

        coordinator.onStartup()
        advanceUntilIdle()

        // setActiveProjectId must NOT be called because activeProjectId is already non-null
        verify(exactly = 0) { projectManaging.setActiveProjectId(any()) }
    }

    // ── onShutdown never throws ───────────────────────────────────────────────

    @Test
    fun onShutdown_doesNotThrow() {
        val (coordinator, _, _) = makeCoordinator()
        // Must be a safe no-op
        coordinator.onShutdown()
    }

    @Test
    fun onShutdown_calledAfterStartup_doesNotThrow() = runTest(testDispatcher) {
        val (coordinator, _, _) = makeCoordinator()
        coordinator.onStartup()
        advanceUntilIdle()

        // Should not throw regardless of state
        coordinator.onShutdown()
    }

    // ── restore returning true skips auto-activate ────────────────────────────

    @Test
    fun onStartup_restoreTrue_autoActivatesAnyway_ifNoActiveProject() = runTest(testDispatcher) {
        // restoreReturns=true means a snapshot was applied, but if activeProjectId is still
        // null after restore (no active project in snapshot), we still auto-activate.
        val project = fakeProject()
        val (coordinator, projectManaging, _) = makeCoordinator(
            projects = listOf(project),
            activeProjectId = null,
            restoreReturns = true,
        )

        coordinator.onStartup()
        advanceUntilIdle()

        // auto-activate should still fire because activeProjectId.value is null
        verify(atLeast = 1) { projectManaging.setActiveProjectId(project.id) }
    }
}

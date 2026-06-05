@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.contract.ProjectsState
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.Project
import studio.vibe.shared.testutil.FakeProjectManaging
import studio.vibe.shared.testutil.FakeTerminalSessionManaging
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class TabItemViewModelTest {

    /** ProjectManaging stub that throws on removeProject */
    private class ThrowingRemoveProjectManaging : ProjectManaging {
        private val _projects = MutableStateFlow<List<Project>>(emptyList())
        private val _activeId = MutableStateFlow<Uuid?>(null)
        private val _recents = MutableStateFlow<List<Project>>(emptyList())
        override val projects: StateFlow<List<Project>> = _projects.asStateFlow()
        override val activeProjectId: StateFlow<Uuid?> = _activeId.asStateFlow()
        override val recentHistory: StateFlow<List<Project>> = _recents.asStateFlow()
        override val recentProjects: StateFlow<List<Project>> = _recents.asStateFlow()
        override val projectsState: StateFlow<ProjectsState> = MutableStateFlow(ProjectsState()).asStateFlow()
        override fun setActiveProjectId(id: Uuid?) {}
        override suspend fun addProject(path: FilePath): Project = throw UnsupportedOperationException()
        override suspend fun removeProject(id: Uuid) = throw RuntimeException("removeProject failed")
        override suspend fun updateProject(id: Uuid, mutate: (Project) -> Project) {}
        override suspend fun moveProjects(fromIndices: Set<Int>, toDestination: Int) {}
        override fun project(id: Uuid): Project? = null
        override fun project(path: FilePath): Project? = null
        override suspend fun load() {}
        override suspend fun save() {}
    }

    private fun buildVm(
        projects: List<Project> = emptyList(),
        pm: FakeProjectManaging = FakeProjectManaging(initialProjects = projects),
        tsm: FakeTerminalSessionManaging = FakeTerminalSessionManaging(),
    ): Triple<TabItemViewModel, FakeProjectManaging, FakeTerminalSessionManaging> {
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        val vm = TabItemViewModel(projectManaging = pm, terminalSessionManaging = tsm, parentScope = scope)
        return Triple(vm, pm, tsm)
    }

    // ── showCloseConfirmation() / hideCloseConfirmation() ─────────────────────────

    @Test
    fun showCloseConfirmation_setsVisibleTrue() {
        val (vm, _, _) = buildVm()

        vm.showCloseConfirmation()

        assertTrue(vm.state.value.isCloseConfirmationVisible)
    }

    @Test
    fun hideCloseConfirmation_setsVisibleFalse() {
        val (vm, _, _) = buildVm()
        vm.showCloseConfirmation()

        vm.hideCloseConfirmation()

        assertFalse(vm.state.value.isCloseConfirmationVisible)
    }

    // ── closeProject() ────────────────────────────────────────────────────────────

    @Test
    fun closeProject_singleProject_activatesNull() = runTest {
        val project = Project(name = "app", path = FilePath("/app"))
        val (vm, pm, _) = buildVm(projects = listOf(project))

        vm.closeProject(project.id)

        assertNull(pm.activeProjectId.value)
        assertTrue(pm.projects.value.isEmpty())
    }

    @Test
    fun closeProject_multipleProjects_activatesPrevious() = runTest {
        val p1 = Project(name = "p1", path = FilePath("/p1"))
        val p2 = Project(name = "p2", path = FilePath("/p2"))
        val pm = FakeProjectManaging(initialProjects = listOf(p1, p2))
        val (vm, _, _) = buildVm(pm = pm)

        // Activate p2, then close p2 — should activate p1 (the one before it)
        pm.setActiveProjectId(p2.id)
        vm.closeProject(p2.id)

        assertEquals(p1.id, pm.activeProjectId.value)
    }

    @Test
    fun closeProject_killsAllTerminalSessions() = runTest {
        val project = Project(name = "app", path = FilePath("/app"))
        val tsm = FakeTerminalSessionManaging()
        tsm.createSession(projectId = project.id, shell = null, workingDirectory = null)
        val (vm, _, _) = buildVm(projects = listOf(project), tsm = tsm)

        vm.closeProject(project.id)

        assertTrue(tsm.sessions(project.id).isEmpty())
    }

    @Test
    fun closeProject_hidesConfirmationDialog() = runTest {
        val project = Project(name = "app", path = FilePath("/app"))
        val (vm, _, _) = buildVm(projects = listOf(project))
        vm.showCloseConfirmation()

        vm.closeProject(project.id)

        assertFalse(vm.state.value.isCloseConfirmationVisible)
    }

    // ── clearError() ─────────────────────────────────────────────────────────────

    @Test
    fun clearError_removesErrorMessage() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        val vm = TabItemViewModel(
            projectManaging = ThrowingRemoveProjectManaging(),
            terminalSessionManaging = FakeTerminalSessionManaging(),
            parentScope = scope,
        )
        vm.closeProject(Uuid.random())
        assertNotNull(vm.state.value.errorMessage)

        vm.clearError()

        assertNull(vm.state.value.errorMessage)
    }
}

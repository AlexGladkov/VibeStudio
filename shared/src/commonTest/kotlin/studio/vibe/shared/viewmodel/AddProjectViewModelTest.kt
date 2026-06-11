@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.project.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.core.common.project.Project
import studio.vibe.shared.core.common.project.ProjectManagerError
import studio.vibe.shared.testutil.FakeProjectManaging
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AddProjectViewModelTest {

    private fun buildVm(
        projects: List<Project> = emptyList(),
        recents: List<Project> = emptyList(),
    ): Pair<AddProjectViewModel, FakeProjectManaging> {
        val dispatcher = UnconfinedTestDispatcher()
        val scope = CoroutineScope(dispatcher)
        val pm = FakeProjectManaging(initialProjects = projects, initialRecents = recents)
        val vm = AddProjectViewModel(projectManaging = pm, parentScope = scope)
        return vm to pm
    }

    @Test
    fun loadRecentProjects_emptyStore_stateHasEmptyList() = runTest {
        val (vm, _) = buildVm()

        assertEquals(emptyList(), vm.state.value.recentProjects)
    }

    @Test
    fun loadRecentProjects_withRecents_populatesState() = runTest {
        val project = Project(name = "myapp", path = FilePath("/projects/myapp"))
        val (vm, _) = buildVm(recents = listOf(project))

        vm.loadRecentProjects()

        assertEquals(1, vm.state.value.recentProjects.size)
    }

    @Test
    fun addProject_validPath_setsOpenedProject() = runTest {
        val (vm, _) = buildVm()

        vm.addProject(FilePath("/new/project"))

        assertNotNull(vm.state.value.openedProject)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun addProject_managerThrowsDuplicate_setsErrorMessage() = runTest {
        val existing = Project(name = "p", path = FilePath("/path"))
        val (vm, pm) = buildVm(projects = listOf(existing))
        pm.addProjectError = ProjectManagerError.Duplicate(
            existingId = existing.id,
            path = existing.path,
        )

        vm.addProject(FilePath("/path"))

        assertNotNull(vm.state.value.errorMessage)
        assertNull(vm.state.value.openedProject)
    }

    @Test
    fun openRecentProject_existingProject_activatesIt() = runTest {
        val project = Project(name = "app", path = FilePath("/app"))
        val (vm, pm) = buildVm(projects = listOf(project))

        vm.openRecentProject(project.path)

        assertEquals(project.id, pm.activeProjectId.value)
    }

    @Test
    fun openRecentProject_nonExistentPath_addsAndActivates() = runTest {
        val (vm, pm) = buildVm()

        vm.openRecentProject(FilePath("/new/path"))

        assertNotNull(vm.state.value.openedProject)
        assertEquals(1, pm.addProjectCallCount)
    }

    @Test
    fun consumeOpened_clearsOpenedProject() = runTest {
        val (vm, _) = buildVm()
        vm.addProject(FilePath("/project"))
        assertNotNull(vm.state.value.openedProject)

        vm.consumeOpened()

        assertNull(vm.state.value.openedProject)
    }

    @Test
    fun clearError_clearsErrorMessage() = runTest {
        val existing = Project(name = "p", path = FilePath("/p"))
        val (vm, pm) = buildVm(projects = listOf(existing))
        pm.addProjectError = ProjectManagerError.Duplicate(existing.id, existing.path)
        vm.addProject(FilePath("/p"))
        assertNotNull(vm.state.value.errorMessage)

        vm.clearError()

        assertNull(vm.state.value.errorMessage)
    }
}

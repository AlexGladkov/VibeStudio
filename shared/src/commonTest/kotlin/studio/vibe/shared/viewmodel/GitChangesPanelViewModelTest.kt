@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.git.presentation

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.core.common.git.GitStatus
import studio.vibe.shared.testutil.FakeGitService
import studio.vibe.shared.testutil.FakeProjectManaging
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitChangesPanelViewModelTest {

    private fun buildVm(
        projects: List<studio.vibe.shared.feature.project.domain.model.Project> = emptyList(),
    ): Triple<GitChangesPanelViewModel, FakeGitService, FakeProjectManaging> {
        val dispatcher = UnconfinedTestDispatcher()
        val scope = kotlinx.coroutines.CoroutineScope(dispatcher)
        val git = FakeGitService()
        val pm = FakeProjectManaging(initialProjects = projects)
        val vm = GitChangesPanelViewModel(
            gitService = git,
            stagingService = git,
            projectManaging = pm,
            parentScope = scope,
        )
        return Triple(vm, git, pm)
    }

    @Test
    fun initialState_isEmpty() = runTest {
        val (vm) = buildVm()
        val state = vm.state.value
        assertEquals(GitStatus.EMPTY, state.status)
        assertTrue(state.diffStats.isEmpty())
        assertNull(state.errorMessage)
    }

    @Test
    fun loadStats_unknownProject_doesNotChangeState() = runTest {
        val (vm) = buildVm()
        val before = vm.state.value
        vm.loadStats(kotlin.uuid.Uuid.random())
        assertEquals(before, vm.state.value)
    }

    @Test
    fun loadStats_knownProject_updatesStatus() = runTest {
        val project = studio.vibe.shared.feature.project.domain.model.Project(
            name = "p",
            path = FilePath("/tmp/repo"),
        )
        val (vm, git) = buildVm(projects = listOf(project))
        val expectedStatus = GitStatus(
            branch = "main",
            aheadCount = 1,
            behindCount = 0,
            stagedFiles = emptyList(),
            unstagedFiles = emptyList(),
            untrackedFiles = emptyList(),
        )
        git.statusResult = expectedStatus
        vm.loadStats(project.id)
        assertEquals(expectedStatus, vm.state.value.status)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun loadStats_onError_setsErrorMessage() = runTest {
        val project = studio.vibe.shared.feature.project.domain.model.Project(
            name = "p",
            path = FilePath("/tmp/repo"),
        )
        val (vm, git) = buildVm(projects = listOf(project))
        git.throwOnStatus = RuntimeException("git not found")
        vm.loadStats(project.id)
        assertTrue(vm.state.value.errorMessage?.contains("git not found") == true)
    }
}

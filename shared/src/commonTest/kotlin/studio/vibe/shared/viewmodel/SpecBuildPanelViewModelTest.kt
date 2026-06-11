@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.codespeak.presentation

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.core.common.project.Project
import studio.vibe.shared.testutil.FakeProcessRunner
import studio.vibe.shared.testutil.FakeProjectManaging
import studio.vibe.shared.feature.codespeak.domain.usecase.RunSpecBuildUseCase
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class SpecBuildPanelViewModelTest {

    private fun buildVm(
        projects: List<Project> = emptyList(),
    ): Triple<SpecBuildPanelViewModel, FakeProcessRunner, FakeProjectManaging> {
        val dispatcher = UnconfinedTestDispatcher()
        val scope = kotlinx.coroutines.CoroutineScope(dispatcher)
        val runner = FakeProcessRunner()
        val pm = FakeProjectManaging(initialProjects = projects)
        val vm = SpecBuildPanelViewModel(
            runSpecBuildUseCase = RunSpecBuildUseCase(runner),
            projectManaging = pm,
            parentScope = scope,
        )
        return Triple(vm, runner, pm)
    }

    @Test
    fun initialState_isNotRunning() {
        val (vm) = buildVm()
        assertFalse(vm.state.value.isRunning)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun runCommand_unknownProject_doesNotRun() = runTest {
        val (vm) = buildVm()
        vm.runCommand(Uuid.random())
        assertFalse(vm.state.value.isRunning)
    }

    @Test
    fun runCommand_withProject_completesWithoutError() = runTest {
        val project = Project(name = "spec-proj", path = FilePath("/tmp/spec"))
        val (vm, runner) = buildVm(projects = listOf(project))
        runner.respondWith(exitCode = 0, stdout = "2/2 specs passing\n", stderr = "")
        vm.runCommand(project.id)
        assertFalse(vm.state.value.isRunning)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun stopCommand_stopsExecution() = runTest {
        val (vm) = buildVm()
        vm.stopCommand()
        assertFalse(vm.state.value.isRunning)
    }
}

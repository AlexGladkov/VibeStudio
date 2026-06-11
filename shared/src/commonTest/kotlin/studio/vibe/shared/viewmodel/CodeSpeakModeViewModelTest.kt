@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.codespeak.presentation

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.core.common.project.Project
import studio.vibe.shared.testutil.FakePersistenceStore
import studio.vibe.shared.testutil.FakeProjectManaging
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CodeSpeakModeViewModelTest {

    private fun buildVm(
        projects: List<Project> = emptyList(),
    ): Triple<CodeSpeakModeViewModel, FakePersistenceStore, FakeProjectManaging> {
        val dispatcher = UnconfinedTestDispatcher()
        val scope = kotlinx.coroutines.CoroutineScope(dispatcher)
        val store = FakePersistenceStore()
        val pm = FakeProjectManaging(initialProjects = projects)
        val vm = CodeSpeakModeViewModel(
            persistenceStore = store,
            projectManaging = pm,
            parentScope = scope,
        )
        return Triple(vm, store, pm)
    }

    @Test
    fun initialState_isEmpty() {
        val (vm) = buildVm()
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.selectedSpec)
        assertTrue(vm.state.value.specs.isEmpty())
    }

    @Test
    fun loadSpecs_unknownProject_doesNotSetLoading() = runTest {
        val (vm) = buildVm()
        vm.loadSpecs(kotlin.uuid.Uuid.random())
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun loadSpecs_knownProject_noSpecsDir_producesEmptySpecs() = runTest {
        val project = Project(name = "proj", path = FilePath("/tmp/proj"))
        val (vm) = buildVm(projects = listOf(project))
        vm.loadSpecs(project.id)
        assertFalse(vm.state.value.isLoading)
        assertTrue(vm.state.value.specs.isEmpty())
    }

    @Test
    fun selectSpec_setsSelectedSpec() = runTest {
        val project = Project(name = "proj", path = FilePath("/tmp/proj"))
        val (vm) = buildVm(projects = listOf(project))
        val specFile = studio.vibe.shared.feature.codespeak.domain.model.SpecFile(
            path = FilePath("/tmp/proj/.specs/auth.cs.md"),
        )
        vm.selectSpec(specFile)
        assertNotNull(vm.state.value.selectedSpec)
    }
}

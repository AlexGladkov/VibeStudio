package studio.vibe.shared.viewmodel

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.testutil.FakePersistenceStore
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TraceabilityPanelViewModelTest {

    private fun buildVm(): Pair<TraceabilityPanelViewModel, FakePersistenceStore> {
        val dispatcher = UnconfinedTestDispatcher()
        val scope = kotlinx.coroutines.CoroutineScope(dispatcher)
        val store = FakePersistenceStore()
        val vm = TraceabilityPanelViewModel(persistenceStore = store, parentScope = scope)
        return Pair(vm, store)
    }

    @Test
    fun initialState_isNotLoading() {
        val (vm) = buildVm()
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.errorMessage)
        assertTrue(vm.state.value.entries.isEmpty())
    }

    @Test
    fun scanProject_emptyDirectory_producesEmptyEntries() = runTest {
        val (vm, store) = buildVm()
        store.addDirectory(FilePath("/project"))
        vm.scanProject(FilePath("/project"))
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun refresh_callsScanProject() = runTest {
        val (vm, store) = buildVm()
        store.addDirectory(FilePath("/project"))
        vm.refresh(FilePath("/project"))
        assertFalse(vm.state.value.isLoading)
    }
}

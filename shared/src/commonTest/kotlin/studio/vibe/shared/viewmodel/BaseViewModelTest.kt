package studio.vibe.shared.core.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies that [BaseViewModel.dispose] cancels the VM's child coroutine scope
 * without affecting the parent scope, and that the parent cancellation also
 * propagates to the child scope.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BaseViewModelTest {

    /**
     * Minimal concrete VM that exposes [exposedScope] for white-box testing.
     * Using a subclass avoids changing [BaseViewModel]'s visibility in production code.
     */
    private class TestViewModel(parentScope: CoroutineScope) : BaseViewModel(parentScope) {
        val exposedScope: CoroutineScope get() = scope
    }

    @Test
    fun dispose_cancels_vm_scope() = runTest {
        val parentScope = TestScope(UnconfinedTestDispatcher())
        val vm = TestViewModel(parentScope)

        assertTrue(
            vm.exposedScope.coroutineContext[Job]?.isActive == true,
            "VM scope should be active before dispose()",
        )

        vm.dispose()

        assertFalse(
            vm.exposedScope.coroutineContext[Job]?.isActive == true,
            "VM scope should be cancelled after dispose()",
        )
    }

    @Test
    fun dispose_does_not_cancel_parent_scope() = runTest {
        val parentScope = TestScope(UnconfinedTestDispatcher())
        val vm = TestViewModel(parentScope)

        vm.dispose()

        // Parent scope must remain alive after the child VM is disposed.
        assertTrue(
            parentScope.coroutineContext[Job]?.isActive == true,
            "Parent scope must remain active after VM dispose()",
        )
    }

    @Test
    fun parent_cancel_propagates_to_vm_scope() {
        val parentJob = SupervisorJob()
        val parentScope = CoroutineScope(parentJob + UnconfinedTestDispatcher())
        val vm = TestViewModel(parentScope)

        assertTrue(vm.exposedScope.coroutineContext[Job]?.isActive == true)

        parentJob.cancel()

        assertFalse(
            vm.exposedScope.coroutineContext[Job]?.isActive == true,
            "VM scope should be cancelled when parent scope is cancelled",
        )
    }

    @Test
    fun dispose_is_idempotent() = runTest {
        val parentScope = TestScope(UnconfinedTestDispatcher())
        val vm = TestViewModel(parentScope)

        // Calling dispose multiple times must not throw.
        vm.dispose()
        vm.dispose()
        vm.dispose()

        assertFalse(vm.exposedScope.coroutineContext[Job]?.isActive == true)
    }
}

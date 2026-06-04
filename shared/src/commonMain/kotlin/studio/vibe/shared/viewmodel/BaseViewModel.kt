package studio.vibe.shared.viewmodel

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Base class for all ViewModels that own a coroutine scope.
 *
 * Creates a **child scope** from the provided [parentScope]:
 * - Inherits the parent's [Job] as parent of the new [SupervisorJob], so the container's
 *   [CoroutineScope.cancel] propagates down automatically.
 * - Can also be cancelled independently via [dispose] without touching the parent scope.
 * - Reuses the parent's [CoroutineDispatcher] when present; falls back to
 *   [Dispatchers.Main.immediate] for production code. This allows test scopes backed by
 *   [UnconfinedTestDispatcher] to propagate their dispatcher into child VMs without
 *   requiring a real Main dispatcher on the test JVM.
 *
 * @param parentScope Application-level scope from [DesktopServiceContainer] or
 *                    a Compose [rememberCoroutineScope]. The VM's scope becomes its child.
 */
abstract class BaseViewModel(parentScope: CoroutineScope) {

    protected val scope: CoroutineScope = CoroutineScope(
        SupervisorJob(parentScope.coroutineContext[Job]) +
            (parentScope.coroutineContext[CoroutineDispatcher] ?: Dispatchers.Main.immediate),
    )

    /**
     * Cancels this ViewModel's coroutine scope.
     *
     * Must be called when the Composable that owns this VM leaves the composition
     * (e.g. from a [DisposableEffect] onDispose block), or when the DI container is torn down.
     *
     * Safe to call multiple times — [CoroutineScope.cancel] is idempotent.
     */
    open fun dispose() {
        scope.cancel()
    }
}

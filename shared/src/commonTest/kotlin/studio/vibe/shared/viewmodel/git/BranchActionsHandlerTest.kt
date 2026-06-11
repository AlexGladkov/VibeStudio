@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.git.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.feature.git.domain.model.GitServiceError
import studio.vibe.shared.testutil.FakeGitServicingBase
import studio.vibe.shared.feature.git.presentation.GitSidebarState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class BranchActionsHandlerTest {

    private fun buildHandler(
        git: studio.vibe.shared.feature.git.domain.contract.GitServicing = FakeGitServicingBase(),
        onLoadGitInfo: (Uuid, FilePath) -> Unit = { _, _ -> },
    ): Pair<BranchActionsHandler, MutableStateFlow<GitSidebarState>> {
        val state = MutableStateFlow(GitSidebarState())
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        val handler = BranchActionsHandler(
            gitService = git,
            scope = scope,
            state = state,
            loadGitInfo = onLoadGitInfo,
        )
        return handler to state
    }

    // ── checkout() ────────────────────────────────────────────────────────────────

    @Test
    fun checkout_success_callsLoadGitInfo() = runTest {
        var loadCalled = false
        val (handler, _) = buildHandler(onLoadGitInfo = { _, _ -> loadCalled = true })

        handler.checkout(Uuid.random(), "main", FilePath("/repo"))

        assertTrue(loadCalled)
    }

    @Test
    fun checkout_failure_setsCheckoutError() = runTest {
        val git = object : FakeGitServicingBase() {
            override suspend fun checkout(branch: String, at: FilePath) {
                throw GitServiceError.CommandFailed("switch", 1, "error: pathspec 'nonexistent' did not match")
            }
        }
        val (handler, state) = buildHandler(git = git)

        handler.checkout(Uuid.random(), "nonexistent", FilePath("/repo"))

        assertNotNull(state.value.checkoutErrorMessage)
    }

    @Test
    fun checkout_whenCurrent_gitServiceCheckoutStillCalled() = runTest {
        var checkoutCalled = false
        val git = object : FakeGitServicingBase() {
            override suspend fun checkout(branch: String, at: FilePath) { checkoutCalled = true }
        }
        val (handler, _) = buildHandler(git = git)

        handler.checkout(Uuid.random(), "main", FilePath("/repo"))

        assertTrue(checkoutCalled)
    }

    // ── requestDeleteBranch() / cancelDeleteBranch() ──────────────────────────────

    @Test
    fun requestDeleteBranch_setsPendingDeleteBranch() {
        val (handler, state) = buildHandler()
        val projectId = Uuid.random()

        handler.requestDeleteBranch(projectId, "feature/foo")

        assertEquals("feature/foo", state.value.pendingDeleteBranch[projectId])
    }

    @Test
    fun cancelDeleteBranch_clearsPendingAndError() {
        val (handler, state) = buildHandler()
        val projectId = Uuid.random()
        handler.requestDeleteBranch(projectId, "feature/foo")

        handler.cancelDeleteBranch(projectId)

        assertNull(state.value.pendingDeleteBranch[projectId])
        assertNull(state.value.deleteErrorMessage)
    }

    // ── confirmDeleteBranch() ─────────────────────────────────────────────────────

    @Test
    fun confirmDeleteBranch_noPending_doesNothing() = runTest {
        val (handler, state) = buildHandler()

        handler.confirmDeleteBranch(Uuid.random(), FilePath("/repo"), force = false)

        assertNull(state.value.deleteErrorMessage)
    }

    @Test
    fun confirmDeleteBranch_force_true_passesForceTrueToService() = runTest {
        var deletedForce: Boolean? = null
        val git = object : FakeGitServicingBase() {
            override suspend fun deleteBranch(name: String, force: Boolean, at: FilePath) { deletedForce = force }
        }
        val (handler, state) = buildHandler(git = git)
        val projectId = Uuid.random()
        handler.requestDeleteBranch(projectId, "stale-branch")

        handler.confirmDeleteBranch(projectId, FilePath("/repo"), force = true)

        assertEquals(true, deletedForce)
        assertNull(state.value.pendingDeleteBranch[projectId])
    }

    @Test
    fun confirmDeleteBranch_force_false_passesForceFalseToService() = runTest {
        var deletedForce: Boolean? = null
        val git = object : FakeGitServicingBase() {
            override suspend fun deleteBranch(name: String, force: Boolean, at: FilePath) { deletedForce = force }
        }
        val (handler, _) = buildHandler(git = git)
        val projectId = Uuid.random()
        handler.requestDeleteBranch(projectId, "merged-branch")

        handler.confirmDeleteBranch(projectId, FilePath("/repo"), force = false)

        assertEquals(false, deletedForce)
    }

    @Test
    fun confirmDeleteBranch_serviceThrows_setsDeleteError() = runTest {
        val git = object : FakeGitServicingBase() {
            override suspend fun deleteBranch(name: String, force: Boolean, at: FilePath) {
                throw GitServiceError.CommandFailed("branch", 1, "branch not fully merged")
            }
        }
        val (handler, state) = buildHandler(git = git)
        val projectId = Uuid.random()
        handler.requestDeleteBranch(projectId, "unmerged")

        handler.confirmDeleteBranch(projectId, FilePath("/repo"), force = false)

        assertNotNull(state.value.deleteErrorMessage)
    }
}

@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.git.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import studio.vibe.shared.feature.git.domain.contract.GitBranching
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.feature.git.domain.model.GitBranch
import studio.vibe.shared.feature.git.domain.model.GitServiceError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CreateBranchViewModelTest {

    // ── Minimal GitBranching fake ─────────────────────────────────────────────────

    private inner class FakeGitBranching(
        private val branchList: List<GitBranch> = emptyList(),
        private val throwOnBranches: Throwable? = null,
        private val throwOnCreate: Throwable? = null,
    ) : GitBranching {
        var checkoutCalled = false

        override suspend fun branches(at: FilePath): List<GitBranch> {
            throwOnBranches?.let { throw it }
            return branchList
        }

        override suspend fun checkout(branch: String, at: FilePath) {
            checkoutCalled = true
        }

        override suspend fun createBranch(name: String, from: String?, at: FilePath) {
            throwOnCreate?.let { throw it }
        }

        override suspend fun deleteBranch(name: String, force: Boolean, at: FilePath) {}
    }

    private fun buildVm(service: FakeGitBranching = FakeGitBranching()): CreateBranchViewModel {
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        return CreateBranchViewModel(gitService = service, parentScope = scope)
    }

    // ── loadBranches() ────────────────────────────────────────────────────────────

    @Test
    fun loadBranches_populatesAvailableBranches() = runTest {
        val branches = listOf(
            GitBranch("main", isRemote = false, isCurrent = true),
            GitBranch("feature/x", isRemote = false, isCurrent = false),
        )
        val vm = buildVm(FakeGitBranching(branchList = branches))

        vm.loadBranches(FilePath("/repo"))

        assertEquals(2, vm.state.value.availableBranches.size)
    }

    @Test
    fun loadBranches_setsBaseBranchToCurrentBranch() = runTest {
        val branches = listOf(
            GitBranch("main", isRemote = false, isCurrent = true),
        )
        val vm = buildVm(FakeGitBranching(branchList = branches))

        vm.loadBranches(FilePath("/repo"))

        assertEquals("main", vm.state.value.baseBranch)
    }

    @Test
    fun loadBranches_nonRepo_setsErrorMessage() = runTest {
        val vm = buildVm(FakeGitBranching(throwOnBranches = GitServiceError.NotARepository(FilePath("/not-a-repo"))))

        vm.loadBranches(FilePath("/not-a-repo"))

        assertNotNull(vm.state.value.errorMessage)
        assertTrue(vm.state.value.availableBranches.isEmpty())
    }

    // ── Branch name validation ────────────────────────────────────────────────────

    @Test
    fun createBranch_emptyName_setsErrorWithoutCallingService() = runTest {
        val fake = FakeGitBranching()
        val vm = buildVm(fake)
        vm.updateBranchName("   ")

        vm.createBranch(FilePath("/repo"))

        assertNotNull(vm.state.value.errorMessage)
        assertFalse(fake.checkoutCalled)
    }

    @Test
    fun createBranch_validName_setsIsCreatedTrue() = runTest {
        val vm = buildVm(FakeGitBranching())
        vm.updateBranchName("new-feature")

        vm.createBranch(FilePath("/repo"))

        assertTrue(vm.state.value.isCreated)
        assertNull(vm.state.value.errorMessage)
        assertFalse(vm.state.value.isCreating)
    }

    @Test
    fun createBranch_serviceThrows_setsErrorMessage() = runTest {
        val vm = buildVm(
            FakeGitBranching(throwOnCreate = GitServiceError.CommandFailed("switch", 1, "already exists")),
        )
        vm.updateBranchName("existing-branch")

        vm.createBranch(FilePath("/repo"))

        assertNotNull(vm.state.value.errorMessage)
        assertFalse(vm.state.value.isCreating)
    }

    // ── updateBaseBranch() ────────────────────────────────────────────────────────

    @Test
    fun updateBaseBranch_updatesState() = runTest {
        val vm = buildVm()

        vm.updateBaseBranch("develop")

        assertEquals("develop", vm.state.value.baseBranch)
    }

    // ── reset() ───────────────────────────────────────────────────────────────────

    @Test
    fun reset_clearsAllState() = runTest {
        val vm = buildVm()
        vm.updateBranchName("some-branch")
        vm.updateBaseBranch("develop")

        vm.reset()

        assertEquals("", vm.state.value.branchName)
        assertNull(vm.state.value.baseBranch)
    }
}

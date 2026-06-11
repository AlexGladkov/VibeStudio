@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.git.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import studio.vibe.shared.core.common.AICommitServicing
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.testutil.FakeGitService
import studio.vibe.shared.testutil.FakeGitServicingBase
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class GitSidebarViewModelTest {

    private fun nopAiCommit() = object : AICommitServicing {
        override suspend fun generateCommitMessage(diff: String) = "feat: auto"
    }

    private fun buildVm(
        gitService: studio.vibe.shared.feature.git.domain.contract.GitServicing = FakeGitService(),
    ): GitSidebarViewModel {
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        return GitSidebarViewModel(
            gitService = gitService,
            aiCommitService = nopAiCommit(),
            parentScope = scope,
        )
    }

    // ── loadGitInfo() ─────────────────────────────────────────────────────────────

    @Test
    fun loadGitInfo_validRepo_updatesGitStatusAndBranches() = runTest {
        val vm = buildVm()
        val projectId = Uuid.random()

        vm.loadGitInfo(projectId, FilePath("/repo"))

        val state = vm.state.value
        assertTrue(projectId in state.projectGitStatuses)
        assertTrue(projectId in state.projectBranches)
        assertFalse(projectId in state.nonGitProjects)
    }

    @Test
    fun loadGitInfo_notARepo_addsToNonGitProjects() = runTest {
        val git = object : FakeGitServicingBase() {
            override suspend fun isRepository(at: FilePath) = false
        }
        val vm = buildVm(gitService = git)
        val projectId = Uuid.random()

        vm.loadGitInfo(projectId, FilePath("/not-a-repo"))

        assertTrue(projectId in vm.state.value.nonGitProjects)
    }

    @Test
    fun loadGitInfo_gitStatusThrows_addsToNonGitProjects() = runTest {
        val git = object : FakeGitServicingBase() {
            override suspend fun status(at: FilePath) = throw RuntimeException("git crash")
        }
        val vm = buildVm(gitService = git)
        val projectId = Uuid.random()

        vm.loadGitInfo(projectId, FilePath("/repo"))

        assertTrue(projectId in vm.state.value.nonGitProjects)
    }

    // ── cleanupProject() ──────────────────────────────────────────────────────────

    @Test
    fun cleanupProject_removesAllProjectState() = runTest {
        val vm = buildVm()
        val projectId = Uuid.random()
        vm.loadGitInfo(projectId, FilePath("/repo"))

        vm.cleanupProject(projectId)

        val state = vm.state.value
        assertFalse(projectId in state.projectGitStatuses)
        assertFalse(projectId in state.projectBranches)
        assertFalse(projectId in state.nonGitProjects)
        assertFalse(projectId in state.gitExpandedProjects)
    }

    // ── refreshAllGitInfo() ───────────────────────────────────────────────────────

    @Test
    fun refreshAllGitInfo_multipleProjects_loadsEach() = runTest {
        val vm = buildVm()
        val p1 = Uuid.random() to FilePath("/repo1")
        val p2 = Uuid.random() to FilePath("/repo2")

        vm.refreshAllGitInfo(listOf(p1, p2))

        val state = vm.state.value
        assertTrue(p1.first in state.projectGitStatuses || p1.first in state.nonGitProjects)
        assertTrue(p2.first in state.projectGitStatuses || p2.first in state.nonGitProjects)
    }

    // ── initRepository() ─────────────────────────────────────────────────────────

    @Test
    fun initRepository_success_doesNotSetError() = runTest {
        val vm = buildVm()
        val projectId = Uuid.random()

        vm.initRepository(projectId, FilePath("/new-repo"))

        assertNull(vm.state.value.commitPanelErrors[projectId])
    }

    @Test
    fun initRepository_gitThrows_setsCommitPanelError() = runTest {
        val git = object : FakeGitServicingBase() {
            override suspend fun initRepository(at: FilePath) = throw RuntimeException("init failed")
        }
        val vm = buildVm(gitService = git)
        val projectId = Uuid.random()

        vm.initRepository(projectId, FilePath("/repo"))

        assertNotNull(vm.state.value.commitPanelErrors[projectId])
    }
}

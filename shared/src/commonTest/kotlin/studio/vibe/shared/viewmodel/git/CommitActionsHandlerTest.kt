@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.git.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import studio.vibe.shared.core.common.AICommitServicing
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.core.common.git.GitFile
import studio.vibe.shared.core.common.git.GitFileStatus
import studio.vibe.shared.core.common.git.GitStatus
import studio.vibe.shared.testutil.FakeGitServicingBase
import studio.vibe.shared.feature.git.presentation.GitSidebarState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class CommitActionsHandlerTest {

    private fun buildHandler(
        git: studio.vibe.shared.feature.git.domain.contract.GitServicing = FakeGitServicingBase(),
        aiCommit: AICommitServicing = object : AICommitServicing {
            override suspend fun generateCommitMessage(diff: String) = "feat: auto message"
        },
        initialState: GitSidebarState = GitSidebarState(),
        onLoadGitInfo: (Uuid, FilePath) -> Unit = { _, _ -> },
    ): Pair<CommitActionsHandler, MutableStateFlow<GitSidebarState>> {
        val state = MutableStateFlow(initialState)
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        val handler = CommitActionsHandler(
            gitService = git,
            aiCommitService = aiCommit,
            scope = scope,
            state = state,
            loadGitInfo = onLoadGitInfo,
        )
        return handler to state
    }

    // ── performCommit() ───────────────────────────────────────────────────────────

    @Test
    fun performCommit_emptySummary_doesNotCallService() = runTest {
        var commitCalled = false
        val git = object : FakeGitServicingBase() {
            override suspend fun commit(message: String, at: FilePath): String { commitCalled = true; return "abc" }
        }
        val (handler, _) = buildHandler(git = git)

        handler.performCommit(Uuid.random(), FilePath("/repo"))

        assertFalse(commitCalled)
    }

    @Test
    fun performCommit_withSummary_callsCommit_andClearsFields() = runTest {
        var committedMsg: String? = null
        val git = object : FakeGitServicingBase() {
            override suspend fun commit(message: String, at: FilePath): String { committedMsg = message; return "abc1234" }
        }
        val projectId = Uuid.random()
        val initialState = GitSidebarState(commitSummaries = mapOf(projectId to "feat: my feature"))
        val (handler, state) = buildHandler(git = git, initialState = initialState)

        handler.performCommit(projectId, FilePath("/repo"))

        assertNotNull(committedMsg)
        assertTrue(committedMsg!!.contains("feat: my feature"))
        assertNull(state.value.commitSummaries[projectId])
        assertFalse(projectId in state.value.committingProjects)
    }

    @Test
    fun performCommit_withEmptyStagedFiles_stagesAllThenCommits() = runTest {
        var stageCalled = false
        var commitCalled = false
        val git = object : FakeGitServicingBase() {
            override suspend fun stage(files: List<String>, at: FilePath) { stageCalled = true }
            override suspend fun commit(message: String, at: FilePath): String { commitCalled = true; return "abc" }
        }
        val projectId = Uuid.random()
        val status = GitStatus(
            branch = "main", aheadCount = 0, behindCount = 0,
            stagedFiles = emptyList(),
            unstagedFiles = listOf(GitFile("src/Foo.kt", GitFileStatus.MODIFIED)),
            untrackedFiles = emptyList(),
        )
        val initialState = GitSidebarState(
            commitSummaries = mapOf(projectId to "fix: bug"),
            projectGitStatuses = mapOf(projectId to status),
        )
        val (handler, _) = buildHandler(git = git, initialState = initialState)

        handler.performCommit(projectId, FilePath("/repo"))

        assertTrue(stageCalled)
        assertTrue(commitCalled)
    }

    @Test
    fun performCommit_gitThrows_setsErrorAndClearsCommitting() = runTest {
        val git = object : FakeGitServicingBase() {
            override suspend fun commit(message: String, at: FilePath): String = throw RuntimeException("nothing to commit")
        }
        val projectId = Uuid.random()
        val initialState = GitSidebarState(commitSummaries = mapOf(projectId to "fix"))
        val (handler, state) = buildHandler(git = git, initialState = initialState)

        handler.performCommit(projectId, FilePath("/repo"))

        assertNotNull(state.value.commitPanelErrors[projectId])
        assertFalse(projectId in state.value.committingProjects)
    }

    // ── generateAICommitMessage() ─────────────────────────────────────────────────

    @Test
    fun generateAICommitMessage_emptyDiff_stillCallsAI() = runTest {
        var aiCalled = false
        val ai = object : AICommitServicing {
            override suspend fun generateCommitMessage(diff: String): String { aiCalled = true; return "chore: empty" }
        }
        val git = object : FakeGitServicingBase() {
            override suspend fun fullStagedDiff(at: FilePath) = ""
            override suspend fun headDiff(at: FilePath) = ""
        }
        val projectId = Uuid.random()
        val (handler, state) = buildHandler(git = git, aiCommit = ai)

        handler.generateAICommitMessage(projectId, FilePath("/repo"))

        assertTrue(aiCalled)
        assertNotNull(state.value.commitSummaries[projectId])
    }

    @Test
    fun generateAICommitMessage_aiThrows_setsCommitPanelError() = runTest {
        val ai = object : AICommitServicing {
            override suspend fun generateCommitMessage(diff: String): String =
                throw studio.vibe.shared.core.common.AICommitServiceError.MissingAPIKey
        }
        val projectId = Uuid.random()
        val (handler, state) = buildHandler(aiCommit = ai)

        handler.generateAICommitMessage(projectId, FilePath("/repo"))

        assertNotNull(state.value.commitPanelErrors[projectId])
        assertFalse(projectId in state.value.generatingAIProjects)
    }
}

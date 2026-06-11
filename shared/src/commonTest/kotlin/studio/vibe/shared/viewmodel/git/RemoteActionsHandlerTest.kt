@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.git.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import studio.vibe.shared.feature.git.domain.contract.UrlConverting
import studio.vibe.shared.core.common.UrlOpening
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.feature.git.domain.model.GitServiceError
import studio.vibe.shared.testutil.FakeGitServicingBase
import studio.vibe.shared.feature.git.presentation.GitSidebarState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class RemoteActionsHandlerTest {

    private val passthroughUrlConverter = object : UrlConverting {
        override fun browserURL(remoteUrl: String): String? = remoteUrl
    }
    private val nullUrlConverter = object : UrlConverting {
        override fun browserURL(remoteUrl: String): String? = null
    }

    private fun buildHandler(
        git: studio.vibe.shared.feature.git.domain.contract.GitServicing = FakeGitServicingBase(),
        urlOpening: UrlOpening? = null,
        urlConverter: UrlConverting = passthroughUrlConverter,
        onLoadGitInfo: (Uuid, FilePath) -> Unit = { _, _ -> },
    ): Pair<RemoteActionsHandler, MutableStateFlow<GitSidebarState>> {
        val state = MutableStateFlow(GitSidebarState())
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        val handler = RemoteActionsHandler(
            gitService = git,
            scope = scope,
            state = state,
            loadGitInfo = onLoadGitInfo,
            urlOpening = urlOpening,
            urlConverter = urlConverter,
        )
        return handler to state
    }

    // ── gitBranchPull() ───────────────────────────────────────────────────────────

    @Test
    fun gitBranchPull_success_callsLoadGitInfo() = runTest {
        var loaded = false
        val (handler, _) = buildHandler(onLoadGitInfo = { _, _ -> loaded = true })

        handler.gitBranchPull(Uuid.random(), "main", isCurrent = true, FilePath("/repo"))

        assertTrue(loaded)
    }

    @Test
    fun gitBranchPull_conflict_setsCommitPanelError() = runTest {
        val git = object : FakeGitServicingBase() {
            override suspend fun pullBranch(branch: String, isCurrent: Boolean, remote: String, at: FilePath) {
                throw GitServiceError.MergeConflict(listOf("CONFLICT (content): Merge conflict in A.kt"))
            }
        }
        val (handler, state) = buildHandler(git = git)
        val projectId = Uuid.random()

        handler.gitBranchPull(projectId, "main", isCurrent = true, FilePath("/repo"))

        assertNotNull(state.value.commitPanelErrors[projectId])
    }

    @Test
    fun gitBranchPull_noUpstream_setsError() = runTest {
        val git = object : FakeGitServicingBase() {
            override suspend fun pullBranch(branch: String, isCurrent: Boolean, remote: String, at: FilePath) {
                throw GitServiceError.CommandFailed("pull", 1, "no tracking information")
            }
        }
        val (handler, state) = buildHandler(git = git)
        val projectId = Uuid.random()

        handler.gitBranchPull(projectId, "feature", isCurrent = false, FilePath("/repo"))

        assertNotNull(state.value.commitPanelErrors[projectId])
    }

    // ── gitBranchPush() ───────────────────────────────────────────────────────────

    @Test
    fun gitBranchPush_success_callsLoadGitInfo() = runTest {
        var loaded = false
        val (handler, _) = buildHandler(onLoadGitInfo = { _, _ -> loaded = true })

        handler.gitBranchPush(Uuid.random(), "feature/x", FilePath("/repo"))

        assertTrue(loaded)
    }

    @Test
    fun gitBranchPush_noUpstream_setsError() = runTest {
        val git = object : FakeGitServicingBase() {
            override suspend fun pushBranch(branch: String, remote: String, at: FilePath) {
                throw GitServiceError.CommandFailed("push", 1, "no upstream branch")
            }
        }
        val (handler, state) = buildHandler(git = git)
        val projectId = Uuid.random()

        handler.gitBranchPush(projectId, "feature/x", FilePath("/repo"))

        assertNotNull(state.value.commitPanelErrors[projectId])
    }

    // ── openInRemote() ────────────────────────────────────────────────────────────

    @Test
    fun openInRemote_withRemoteUrl_opensInBrowser() = runTest {
        val git = object : FakeGitServicingBase() {
            override suspend fun remoteURL(name: String, at: FilePath) = "https://github.com/user/repo.git"
        }
        val urlOpening = object : UrlOpening {
            val opened = mutableListOf<String>()
            override fun openUrl(url: String) { opened.add(url) }
        }
        val (handler, _) = buildHandler(git = git, urlOpening = urlOpening)

        handler.openInRemote(Uuid.random(), FilePath("/repo"))

        assertFalse(urlOpening.opened.isEmpty())
    }

    @Test
    fun openInRemote_nullRemoteUrl_doesNotOpenBrowser() = runTest {
        val git = object : FakeGitServicingBase() {
            override suspend fun remoteURL(name: String, at: FilePath): String? = null
        }
        val urlOpening = object : UrlOpening {
            val opened = mutableListOf<String>()
            override fun openUrl(url: String) { opened.add(url) }
        }
        val (handler, _) = buildHandler(git = git, urlOpening = urlOpening)

        handler.openInRemote(Uuid.random(), FilePath("/repo"))

        assertTrue(urlOpening.opened.isEmpty())
    }

    @Test
    fun openInRemote_converterReturnsNull_doesNotOpenBrowser() = runTest {
        val git = object : FakeGitServicingBase() {
            override suspend fun remoteURL(name: String, at: FilePath) = "git@github.com:user/repo.git"
        }
        val urlOpening = object : UrlOpening {
            val opened = mutableListOf<String>()
            override fun openUrl(url: String) { opened.add(url) }
        }
        val (handler, _) = buildHandler(git = git, urlOpening = urlOpening, urlConverter = nullUrlConverter)

        handler.openInRemote(Uuid.random(), FilePath("/repo"))

        assertTrue(urlOpening.opened.isEmpty())
    }
}

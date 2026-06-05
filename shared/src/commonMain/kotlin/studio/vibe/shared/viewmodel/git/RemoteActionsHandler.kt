package studio.vibe.shared.viewmodel.git

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import studio.vibe.shared.contract.GitServicing
import studio.vibe.shared.contract.UrlConverting
import studio.vibe.shared.contract.UrlOpening
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.viewmodel.GitSidebarState
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Handles remote-related actions: push branch, pull branch, fetch, open in browser.
 *
 * Mutates [state] directly and calls [loadGitInfo] to refresh after each operation.
 * Stateless aside from delegating to [gitService], optional [urlOpening], and [scope].
 */
@OptIn(ExperimentalUuidApi::class)
internal class RemoteActionsHandler(
    private val gitService: GitServicing,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<GitSidebarState>,
    private val loadGitInfo: (Uuid, FilePath) -> Unit,
    private val urlOpening: UrlOpening? = null,
    private val urlConverter: UrlConverting,
) {

    fun gitBranchPull(projectId: Uuid, branch: String, isCurrent: Boolean, path: FilePath) {
        scope.launch {
            runCatching {
                val remote = gitService.defaultRemote(forBranch = branch, at = path)
                gitService.pullBranch(branch = branch, isCurrent = isCurrent, remote = remote, at = path)
                loadGitInfo(projectId, path)
            }.onFailure { e ->
                state.update { s ->
                    s.copy(
                        commitPanelErrors = s.commitPanelErrors + (projectId to (e.message ?: "Pull failed"))
                    )
                }
            }
        }
    }

    fun gitBranchPush(projectId: Uuid, branch: String, path: FilePath) {
        scope.launch {
            runCatching {
                val remote = gitService.defaultRemote(forBranch = branch, at = path)
                gitService.pushBranch(branch = branch, remote = remote, at = path)
                loadGitInfo(projectId, path)
            }.onFailure { e ->
                state.update { s ->
                    s.copy(
                        commitPanelErrors = s.commitPanelErrors + (projectId to (e.message ?: "Push failed"))
                    )
                }
            }
        }
    }

    fun openInRemote(projectId: Uuid, path: FilePath) {
        scope.launch {
            runCatching {
                val rawUrl = gitService.remoteURL(name = "origin", at = path)
                if (rawUrl != null) {
                    val browserUrl = urlConverter.browserURL(rawUrl)
                    if (browserUrl != null) {
                        urlOpening?.openUrl(browserUrl)
                    }
                }
            }
        }
    }
}

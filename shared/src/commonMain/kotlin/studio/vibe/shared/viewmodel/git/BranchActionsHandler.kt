package studio.vibe.shared.viewmodel.git

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import studio.vibe.shared.contract.GitServicing
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.viewmodel.GitSidebarState
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Handles branch-related actions: checkout, create, delete (two-step confirm flow).
 *
 * Mutates [state] directly and calls [loadGitInfo] to refresh after each operation.
 * Stateless aside from delegating to [gitService] and [scope].
 */
@OptIn(ExperimentalUuidApi::class)
internal class BranchActionsHandler(
    private val gitService: GitServicing,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<GitSidebarState>,
    private val loadGitInfo: (Uuid, FilePath) -> Unit,
) {

    fun checkout(projectId: Uuid, branch: String, path: FilePath) {
        scope.launch {
            runCatching {
                gitService.checkout(branch = branch, at = path)
                loadGitInfo(projectId, path)
                state.update { s -> s.copy(checkoutErrorMessage = null) }
            }.onFailure { e ->
                state.update { s -> s.copy(checkoutErrorMessage = e.message) }
            }
        }
    }

    fun requestDeleteBranch(projectId: Uuid, branchName: String) {
        state.update { s ->
            s.copy(pendingDeleteBranch = s.pendingDeleteBranch + (projectId to branchName))
        }
    }

    fun cancelDeleteBranch(projectId: Uuid) {
        state.update { s ->
            s.copy(
                pendingDeleteBranch = s.pendingDeleteBranch - projectId,
                deleteErrorMessage = null,
            )
        }
    }

    fun confirmDeleteBranch(projectId: Uuid, path: FilePath, force: Boolean = false) {
        val branchName = state.value.pendingDeleteBranch[projectId] ?: return
        scope.launch {
            runCatching {
                gitService.deleteBranch(name = branchName, force = force, at = path)
                state.update { s ->
                    s.copy(
                        pendingDeleteBranch = s.pendingDeleteBranch - projectId,
                        deleteErrorMessage = null,
                    )
                }
                loadGitInfo(projectId, path)
            }.onFailure { e ->
                state.update { s -> s.copy(deleteErrorMessage = e.message) }
            }
        }
    }

    fun clearCheckoutError() {
        state.update { s -> s.copy(checkoutErrorMessage = null) }
    }

    fun clearDeleteError() {
        state.update { s -> s.copy(deleteErrorMessage = null) }
    }
}

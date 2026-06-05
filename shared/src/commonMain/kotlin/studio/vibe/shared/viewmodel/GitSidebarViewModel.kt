package studio.vibe.shared.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import studio.vibe.shared.contract.AICommitServicing
import studio.vibe.shared.contract.GitServicing
import studio.vibe.shared.contract.UrlConverting
import studio.vibe.shared.contract.UrlOpening
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.GitBranch
import studio.vibe.shared.model.GitStatus
import studio.vibe.shared.service.git.GitURLConverter
import studio.vibe.shared.viewmodel.git.BranchActionsHandler
import studio.vibe.shared.viewmodel.git.CommitActionsHandler
import studio.vibe.shared.viewmodel.git.RemoteActionsHandler
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class GitSidebarState(
    val gitExpandedProjects: Set<Uuid> = emptySet(),
    val projectGitStatuses: Map<Uuid, GitStatus> = emptyMap(),
    val projectBranches: Map<Uuid, List<GitBranch>> = emptyMap(),
    val nonGitProjects: Set<Uuid> = emptySet(),
    val remoteUnavailableProjects: Set<Uuid> = emptySet(),
    val commitSummaries: Map<Uuid, String> = emptyMap(),
    val commitDescriptions: Map<Uuid, String> = emptyMap(),
    val commitPanelErrors: Map<Uuid, String> = emptyMap(),
    val generatingAIProjects: Set<Uuid> = emptySet(),
    val committingProjects: Set<Uuid> = emptySet(),
    val checkoutErrorMessage: String? = null,
    /**
     * Branch name pending delete confirmation per project.
     * Non-null means a confirmation dialog should be shown.
     * Cleared after user confirms or cancels.
     */
    val pendingDeleteBranch: Map<Uuid, String> = emptyMap(),
    val deleteErrorMessage: String? = null,
)

/**
 * Thin orchestrator: owns [GitSidebarState] and delegates all actions to focused handlers.
 *
 * Public API is identical to the previous monolithic implementation — all callers
 * (UI, DI, tests) are unaffected.
 */
@OptIn(ExperimentalUuidApi::class)
class GitSidebarViewModel(
    private val gitService: GitServicing,
    private val aiCommitService: AICommitServicing,
    parentScope: CoroutineScope,
    private val urlOpening: UrlOpening? = null,
    private val urlConverter: UrlConverting = GitURLConverter,
) : BaseViewModel(parentScope) {

    private val _state = MutableStateFlow(GitSidebarState())
    val state: StateFlow<GitSidebarState> = _state.asStateFlow()

    // ── Action handlers ────────────────────────────────────────────────────────

    private val branchActions = BranchActionsHandler(
        gitService = gitService,
        scope = scope,
        state = _state,
        loadGitInfo = ::loadGitInfo,
    )

    private val commitActions = CommitActionsHandler(
        gitService = gitService,
        aiCommitService = aiCommitService,
        scope = scope,
        state = _state,
        loadGitInfo = ::loadGitInfo,
    )

    private val remoteActions = RemoteActionsHandler(
        gitService = gitService,
        scope = scope,
        state = _state,
        loadGitInfo = ::loadGitInfo,
        urlOpening = urlOpening,
        urlConverter = urlConverter,
    )

    // ── State helpers ──────────────────────────────────────────────────────────

    fun toggleExpanded(projectId: Uuid) {
        _state.update { s ->
            val expanded = s.gitExpandedProjects.toMutableSet()
            if (projectId in expanded) expanded.remove(projectId) else expanded.add(projectId)
            s.copy(gitExpandedProjects = expanded)
        }
    }

    fun loadGitInfo(projectId: Uuid, path: FilePath) {
        scope.launch {
            runCatching {
                val isRepo = gitService.isRepository(at = path)
                if (!isRepo) {
                    _state.update { s ->
                        s.copy(nonGitProjects = s.nonGitProjects + projectId)
                    }
                    return@launch
                }
                val status = gitService.status(at = path)
                val branches = gitService.branches(at = path)
                _state.update { s ->
                    s.copy(
                        projectGitStatuses = s.projectGitStatuses + (projectId to status),
                        projectBranches = s.projectBranches + (projectId to branches),
                        nonGitProjects = s.nonGitProjects - projectId,
                    )
                }
                // Check remote availability
                runCatching {
                    gitService.remoteURL(name = "origin", at = path)
                }.onFailure {
                    _state.update { s ->
                        s.copy(remoteUnavailableProjects = s.remoteUnavailableProjects + projectId)
                    }
                }.onSuccess { url ->
                    if (url == null) {
                        _state.update { s ->
                            s.copy(remoteUnavailableProjects = s.remoteUnavailableProjects + projectId)
                        }
                    } else {
                        _state.update { s ->
                            s.copy(remoteUnavailableProjects = s.remoteUnavailableProjects - projectId)
                        }
                    }
                }
            }.onFailure {
                _state.update { s ->
                    s.copy(nonGitProjects = s.nonGitProjects + projectId)
                }
            }
        }
    }

    fun refreshAllGitInfo(projects: List<Pair<Uuid, FilePath>>) {
        projects.forEach { (id, path) -> loadGitInfo(id, path) }
    }

    fun cleanupProject(projectId: Uuid) {
        _state.update { s ->
            s.copy(
                gitExpandedProjects = s.gitExpandedProjects - projectId,
                projectGitStatuses = s.projectGitStatuses - projectId,
                projectBranches = s.projectBranches - projectId,
                nonGitProjects = s.nonGitProjects - projectId,
                remoteUnavailableProjects = s.remoteUnavailableProjects - projectId,
                commitSummaries = s.commitSummaries - projectId,
                commitDescriptions = s.commitDescriptions - projectId,
                commitPanelErrors = s.commitPanelErrors - projectId,
                generatingAIProjects = s.generatingAIProjects - projectId,
                committingProjects = s.committingProjects - projectId,
                pendingDeleteBranch = s.pendingDeleteBranch - projectId,
            )
        }
    }

    // ── Branch delegation ──────────────────────────────────────────────────────

    fun checkout(projectId: Uuid, branch: String, path: FilePath) =
        branchActions.checkout(projectId, branch, path)

    fun checkoutBranch(projectId: Uuid, branchName: String, path: FilePath) =
        branchActions.checkout(projectId, branchName, path)

    fun requestDeleteBranch(projectId: Uuid, branchName: String) =
        branchActions.requestDeleteBranch(projectId, branchName)

    fun cancelDeleteBranch(projectId: Uuid) =
        branchActions.cancelDeleteBranch(projectId)

    fun confirmDeleteBranch(projectId: Uuid, path: FilePath, force: Boolean = false) =
        branchActions.confirmDeleteBranch(projectId, path, force)

    fun clearCheckoutError() =
        branchActions.clearCheckoutError()

    fun clearDeleteError() =
        branchActions.clearDeleteError()

    // ── Commit delegation ──────────────────────────────────────────────────────

    fun performCommit(projectId: Uuid, path: FilePath) =
        commitActions.performCommit(projectId, path)

    fun generateAICommitMessage(projectId: Uuid, path: FilePath) =
        commitActions.generateAICommitMessage(projectId, path)

    fun sendAIDiff(projectId: Uuid, path: FilePath) =
        commitActions.generateAICommitMessage(projectId, path)

    fun updateCommitSummary(projectId: Uuid, value: String) =
        commitActions.updateCommitSummary(projectId, value)

    fun updateCommitDescription(projectId: Uuid, value: String) =
        commitActions.updateCommitDescription(projectId, value)

    // ── Remote delegation ──────────────────────────────────────────────────────

    fun gitBranchPull(projectId: Uuid, branch: String, isCurrent: Boolean, path: FilePath) =
        remoteActions.gitBranchPull(projectId, branch, isCurrent, path)

    fun gitBranchPush(projectId: Uuid, branch: String, path: FilePath) =
        remoteActions.gitBranchPush(projectId, branch, path)

    fun openInRemote(projectId: Uuid, path: FilePath) =
        remoteActions.openInRemote(projectId, path)

    // ── Repository init ────────────────────────────────────────────────────────

    fun initRepository(projectId: Uuid, path: FilePath) {
        scope.launch {
            runCatching {
                gitService.initRepository(at = path)
                loadGitInfo(projectId, path)
            }.onFailure { e ->
                _state.update { s ->
                    s.copy(
                        commitPanelErrors = s.commitPanelErrors + (projectId to (e.message ?: "Init failed"))
                    )
                }
            }
        }
    }
}

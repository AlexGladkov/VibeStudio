package studio.vibe.shared.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import studio.vibe.shared.contract.AICommitServicing
import studio.vibe.shared.contract.GitServicing
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.GitBranch
import studio.vibe.shared.model.GitStatus
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
)

@OptIn(ExperimentalUuidApi::class)
class GitSidebarViewModel(
    private val gitService: GitServicing,
    private val aiCommitService: AICommitServicing,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(GitSidebarState())
    val state: StateFlow<GitSidebarState> = _state.asStateFlow()

    /** Platform callback for opening URLs (replaces NSWorkspace.openURL) */
    var onOpenURL: ((String) -> Unit)? = null

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
            }.onFailure { e ->
                _state.update { s ->
                    s.copy(nonGitProjects = s.nonGitProjects + projectId)
                }
            }
        }
    }

    fun refreshAllGitInfo(projects: List<Pair<Uuid, FilePath>>) {
        projects.forEach { (id, path) -> loadGitInfo(id, path) }
    }

    fun checkout(projectId: Uuid, branch: String, path: FilePath) {
        scope.launch {
            runCatching {
                gitService.checkout(branch = branch, at = path)
                loadGitInfo(projectId, path)
                _state.update { s -> s.copy(checkoutErrorMessage = null) }
            }.onFailure { e ->
                _state.update { s -> s.copy(checkoutErrorMessage = e.message) }
            }
        }
    }

    fun gitBranchPull(projectId: Uuid, branch: String, isCurrent: Boolean, path: FilePath) {
        scope.launch {
            runCatching {
                val remote = gitService.defaultRemote(forBranch = branch, at = path)
                gitService.pullBranch(branch = branch, isCurrent = isCurrent, remote = remote, at = path)
                loadGitInfo(projectId, path)
            }.onFailure { e ->
                _state.update { s ->
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
                _state.update { s ->
                    s.copy(
                        commitPanelErrors = s.commitPanelErrors + (projectId to (e.message ?: "Push failed"))
                    )
                }
            }
        }
    }

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

    fun performCommit(projectId: Uuid, path: FilePath) {
        val summary = _state.value.commitSummaries[projectId].orEmpty()
        if (summary.isBlank()) return

        scope.launch {
            _state.update { s ->
                s.copy(committingProjects = s.committingProjects + projectId)
            }
            runCatching {
                val description = _state.value.commitDescriptions[projectId].orEmpty()
                val message = if (description.isBlank()) summary
                    else "$summary\n\n$description"

                val status = _state.value.projectGitStatuses[projectId]
                if (status != null && status.stagedFiles.isEmpty()) {
                    val allFiles = (status.unstagedFiles + status.untrackedFiles).map { it.path }
                    if (allFiles.isNotEmpty()) {
                        gitService.stage(files = allFiles, at = path)
                    }
                }
                gitService.commit(message = message, at = path)
                _state.update { s ->
                    s.copy(
                        commitSummaries = s.commitSummaries - projectId,
                        commitDescriptions = s.commitDescriptions - projectId,
                        commitPanelErrors = s.commitPanelErrors - projectId,
                    )
                }
                loadGitInfo(projectId, path)
            }.onFailure { e ->
                _state.update { s ->
                    s.copy(
                        commitPanelErrors = s.commitPanelErrors + (projectId to (e.message ?: "Commit failed"))
                    )
                }
            }
            _state.update { s ->
                s.copy(committingProjects = s.committingProjects - projectId)
            }
        }
    }

    fun generateAICommitMessage(projectId: Uuid, path: FilePath) {
        scope.launch {
            _state.update { s ->
                s.copy(generatingAIProjects = s.generatingAIProjects + projectId)
            }
            runCatching {
                val diff = gitService.fullStagedDiff(at = path)
                    .ifBlank { gitService.headDiff(at = path) }
                val message = aiCommitService.generateCommitMessage(diff = diff)
                val lines = message.lines()
                val summary = lines.firstOrNull().orEmpty()
                val description = lines.drop(2).joinToString("\n").trimEnd()
                _state.update { s ->
                    s.copy(
                        commitSummaries = s.commitSummaries + (projectId to summary),
                        commitDescriptions = if (description.isNotBlank())
                            s.commitDescriptions + (projectId to description)
                        else s.commitDescriptions,
                    )
                }
            }.onFailure { e ->
                _state.update { s ->
                    s.copy(
                        commitPanelErrors = s.commitPanelErrors + (projectId to (e.message ?: "AI generation failed"))
                    )
                }
            }
            _state.update { s ->
                s.copy(generatingAIProjects = s.generatingAIProjects - projectId)
            }
        }
    }

    fun sendAIDiff(projectId: Uuid, path: FilePath) {
        generateAICommitMessage(projectId, path)
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
            )
        }
    }

    fun updateCommitSummary(projectId: Uuid, value: String) {
        _state.update { s ->
            s.copy(commitSummaries = s.commitSummaries + (projectId to value))
        }
    }

    fun updateCommitDescription(projectId: Uuid, value: String) {
        _state.update { s ->
            s.copy(commitDescriptions = s.commitDescriptions + (projectId to value))
        }
    }

    fun clearCheckoutError() {
        _state.update { s -> s.copy(checkoutErrorMessage = null) }
    }

    fun openInRemote(projectId: Uuid, path: FilePath) {
        scope.launch {
            runCatching {
                val url = gitService.remoteURL(name = "origin", at = path)
                if (url != null) {
                    onOpenURL?.invoke(url)
                }
            }
        }
    }
}

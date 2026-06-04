package studio.vibe.shared.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import studio.vibe.shared.contract.GitStaging
import studio.vibe.shared.contract.GitStatusQuerying
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.GitDiffStat
import studio.vibe.shared.model.GitStatus
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class GitChangesPanelState(
    val status: GitStatus = GitStatus.EMPTY,
    val diffStats: Map<String, GitDiffStat> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalUuidApi::class)
class GitChangesPanelViewModel(
    private val gitService: GitStatusQuerying,
    private val stagingService: GitStaging,
    private val projectManaging: ProjectManaging,
    parentScope: CoroutineScope,
) : BaseViewModel(parentScope) {

    private val _state = MutableStateFlow(GitChangesPanelState())
    val state: StateFlow<GitChangesPanelState> = _state.asStateFlow()

    fun loadStats(projectId: Uuid) {
        val project = projectManaging.project(projectId) ?: return
        scope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val status = gitService.status(at = project.path)
                val stats = gitService.diffStats(at = project.path)
                _state.update { it.copy(status = status, diffStats = stats, isLoading = false) }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun stageFiles(files: List<String>, projectId: Uuid) {
        val project = projectManaging.project(projectId) ?: return
        scope.launch {
            runCatching {
                stagingService.stage(files = files, at = project.path)
                loadStats(projectId)
            }.onFailure { e ->
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun unstageFiles(files: List<String>, projectId: Uuid) {
        val project = projectManaging.project(projectId) ?: return
        scope.launch {
            runCatching {
                stagingService.unstage(files = files, at = project.path)
                loadStats(projectId)
            }.onFailure { e ->
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun stageAll(projectId: Uuid) {
        val project = projectManaging.project(projectId) ?: return
        scope.launch {
            runCatching {
                val status = _state.value.status
                val allFiles = (status.unstagedFiles + status.untrackedFiles).map { it.path }
                if (allFiles.isNotEmpty()) {
                    stagingService.stage(files = allFiles, at = project.path)
                    loadStats(projectId)
                }
            }.onFailure { e ->
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun unstageAll(projectId: Uuid) {
        val project = projectManaging.project(projectId) ?: return
        scope.launch {
            runCatching {
                val staged = _state.value.status.stagedFiles.map { it.path }
                if (staged.isNotEmpty()) {
                    stagingService.unstage(files = staged, at = project.path)
                    loadStats(projectId)
                }
            }.onFailure { e ->
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}

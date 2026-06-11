package studio.vibe.shared.feature.git.presentation

import kotlinx.coroutines.CoroutineScope
import studio.vibe.shared.core.common.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import studio.vibe.shared.feature.git.domain.contract.GitBranching
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.feature.git.domain.model.GitBranch
import studio.vibe.shared.feature.git.presentation.CreateBranchState
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class CreateBranchViewModel(
    private val gitService: GitBranching,
    parentScope: CoroutineScope,
) : BaseViewModel(parentScope) {

    private val _state = MutableStateFlow(CreateBranchState())
    val state: StateFlow<CreateBranchState> = _state.asStateFlow()

    fun loadBranches(path: FilePath) {
        scope.launch {
            runCatching {
                val branches = gitService.branches(at = path)
                val currentBranch = branches.firstOrNull { it.isCurrent }?.name
                _state.update { s ->
                    s.copy(
                        availableBranches = branches,
                        baseBranch = currentBranch ?: s.baseBranch,
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun updateBranchName(name: String) {
        _state.update { it.copy(branchName = name, errorMessage = null) }
    }

    fun updateBaseBranch(branch: String) {
        _state.update { it.copy(baseBranch = branch) }
    }

    fun createBranch(path: FilePath) {
        val name = _state.value.branchName.trim()
        if (name.isBlank()) {
            _state.update { it.copy(errorMessage = "Branch name cannot be empty") }
            return
        }
        scope.launch {
            _state.update { it.copy(isCreating = true, errorMessage = null) }
            runCatching {
                gitService.createBranch(
                    name = name,
                    from = _state.value.baseBranch,
                    at = path,
                )
                gitService.checkout(branch = name, at = path)
                _state.update { it.copy(isCreating = false, isCreated = true) }
            }.onFailure { e ->
                _state.update { it.copy(isCreating = false, errorMessage = e.message) }
            }
        }
    }

    fun reset() {
        _state.update { CreateBranchState() }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}

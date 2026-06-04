package studio.vibe.shared.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import studio.vibe.shared.contract.GitServicing
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.GitBranch
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
data class CreateBranchState(
    val branchName: String = "",
    val baseBranch: String? = null,
    val availableBranches: List<GitBranch> = emptyList(),
    val isCreating: Boolean = false,
    val isCreated: Boolean = false,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalUuidApi::class)
class CreateBranchViewModel(
    private val gitService: GitServicing,
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

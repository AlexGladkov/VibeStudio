package studio.vibe.shared.feature.git.presentation

import studio.vibe.shared.feature.git.domain.model.GitBranch
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

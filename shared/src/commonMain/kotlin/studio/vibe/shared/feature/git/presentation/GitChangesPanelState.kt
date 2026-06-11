package studio.vibe.shared.feature.git.presentation

import studio.vibe.shared.feature.git.domain.model.GitDiffStat
import studio.vibe.shared.core.common.git.GitStatus
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
data class GitChangesPanelState(
    val status: GitStatus = GitStatus.EMPTY,
    val diffStats: Map<String, GitDiffStat> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

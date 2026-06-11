package studio.vibe.shared.feature.git.presentation

import studio.vibe.shared.feature.git.domain.model.GitBranch
import studio.vibe.shared.feature.git.domain.model.GitDiffStat
import studio.vibe.shared.core.common.git.GitStatus
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class GitSidebarState(
    val gitExpandedProjects: Set<Uuid> = emptySet(),
    val projectGitStatuses: Map<Uuid, GitStatus> = emptyMap(),
    val projectDiffStats: Map<Uuid, Map<String, GitDiffStat>> = emptyMap(),
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

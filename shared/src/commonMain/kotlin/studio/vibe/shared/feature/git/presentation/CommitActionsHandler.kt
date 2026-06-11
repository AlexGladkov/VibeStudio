package studio.vibe.shared.feature.git.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import studio.vibe.shared.core.common.AICommitServicing
import studio.vibe.shared.feature.git.domain.contract.GitServicing
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.feature.git.presentation.GitSidebarState
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Handles commit-related actions: stage-all + commit, AI message generation.
 *
 * Mutates [state] directly and calls [loadGitInfo] to refresh after commit.
 * Stateless aside from delegating to [gitService], [aiCommitService], and [scope].
 */
@OptIn(ExperimentalUuidApi::class)
internal class CommitActionsHandler(
    private val gitService: GitServicing,
    private val aiCommitService: AICommitServicing,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<GitSidebarState>,
    private val loadGitInfo: (Uuid, FilePath) -> Unit,
) {

    fun performCommit(projectId: Uuid, path: FilePath) {
        val snapshot = state.value
        val summary = snapshot.commitSummaries[projectId].orEmpty()
        if (summary.isBlank()) return

        scope.launch {
            state.update { s ->
                s.copy(committingProjects = s.committingProjects + projectId)
            }
            runCatching {
                val description = snapshot.commitDescriptions[projectId].orEmpty()
                val message = if (description.isBlank()) summary
                    else "$summary\n\n$description"

                val status = snapshot.projectGitStatuses[projectId]
                if (status != null && status.stagedFiles.isEmpty()) {
                    val allFiles = (status.unstagedFiles + status.untrackedFiles).map { it.path }
                    if (allFiles.isNotEmpty()) {
                        gitService.stage(files = allFiles, at = path)
                    }
                }
                gitService.commit(message = message, at = path)
                state.update { s ->
                    s.copy(
                        commitSummaries = s.commitSummaries - projectId,
                        commitDescriptions = s.commitDescriptions - projectId,
                        commitPanelErrors = s.commitPanelErrors - projectId,
                    )
                }
                loadGitInfo(projectId, path)
            }.onFailure { e ->
                state.update { s ->
                    s.copy(
                        commitPanelErrors = s.commitPanelErrors + (projectId to (e.message ?: "Commit failed"))
                    )
                }
            }
            state.update { s ->
                s.copy(committingProjects = s.committingProjects - projectId)
            }
        }
    }

    fun generateAICommitMessage(projectId: Uuid, path: FilePath) {
        scope.launch {
            state.update { s ->
                s.copy(generatingAIProjects = s.generatingAIProjects + projectId)
            }
            runCatching {
                val diff = gitService.fullStagedDiff(at = path)
                    .ifBlank { gitService.headDiff(at = path) }
                val message = aiCommitService.generateCommitMessage(diff = diff)
                val lines = message.lines()
                val summary = lines.firstOrNull().orEmpty()
                val description = lines.drop(2).joinToString("\n").trimEnd()
                state.update { s ->
                    s.copy(
                        commitSummaries = s.commitSummaries + (projectId to summary),
                        commitDescriptions = if (description.isNotBlank())
                            s.commitDescriptions + (projectId to description)
                        else s.commitDescriptions,
                    )
                }
            }.onFailure { e ->
                state.update { s ->
                    s.copy(
                        commitPanelErrors = s.commitPanelErrors + (projectId to (e.message ?: "AI generation failed"))
                    )
                }
            }
            state.update { s ->
                s.copy(generatingAIProjects = s.generatingAIProjects - projectId)
            }
        }
    }

    fun updateCommitSummary(projectId: Uuid, value: String) {
        state.update { s ->
            s.copy(commitSummaries = s.commitSummaries + (projectId to value))
        }
    }

    fun updateCommitDescription(projectId: Uuid, value: String) {
        state.update { s ->
            s.copy(commitDescriptions = s.commitDescriptions + (projectId to value))
        }
    }
}

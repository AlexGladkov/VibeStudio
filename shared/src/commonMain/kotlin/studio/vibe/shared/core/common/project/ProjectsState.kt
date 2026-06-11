@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.core.common.project

import studio.vibe.shared.core.common.project.Project
import kotlin.uuid.Uuid

/** Unified snapshot of project-manager state. */
data class ProjectsState(
    val projects: List<Project> = emptyList(),
    val activeProjectId: Uuid? = null,
    val recentHistory: List<Project> = emptyList(),
    val recentProjects: List<Project> = emptyList(),
) {
    companion object {
        val EMPTY = ProjectsState()
    }
}

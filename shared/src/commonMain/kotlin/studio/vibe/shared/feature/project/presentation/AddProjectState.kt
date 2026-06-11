@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.project.presentation

import studio.vibe.shared.core.common.project.Project

data class AddProjectState(
    val recentProjects: List<Project> = emptyList(),
    val errorMessage: String? = null,
    val openedProject: Project? = null,
)

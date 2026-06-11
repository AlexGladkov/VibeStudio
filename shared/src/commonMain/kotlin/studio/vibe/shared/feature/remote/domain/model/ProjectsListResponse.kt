package studio.vibe.shared.feature.remote.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProjectsListResponse(
    val projects: List<ProjectResponse>,
    @SerialName("active_project_id") val activeProjectId: String? = null,
)

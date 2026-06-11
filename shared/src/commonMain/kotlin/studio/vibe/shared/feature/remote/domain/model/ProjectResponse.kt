package studio.vibe.shared.feature.remote.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProjectResponse(
    val id: String,
    val name: String,
    val path: String,
    val color: String? = null,
    @SerialName("is_active") val isActive: Boolean,
    val git: GitInfoResponse? = null,
    val sessions: List<SessionResponse>,
)

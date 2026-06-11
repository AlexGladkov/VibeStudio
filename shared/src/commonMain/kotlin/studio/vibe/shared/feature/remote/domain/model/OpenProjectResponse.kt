package studio.vibe.shared.feature.remote.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenProjectResponse(
    val ok: Boolean,
    @SerialName("project_id") val projectId: String,
)

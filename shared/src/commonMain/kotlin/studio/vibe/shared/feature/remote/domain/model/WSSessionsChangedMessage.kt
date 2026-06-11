package studio.vibe.shared.feature.remote.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WSSessionsChangedMessage(
    val type: String,
    @SerialName("project_id") val projectId: String,
    val sessions: List<SessionResponse>,
)

package studio.vibe.shared.feature.remote.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RecentProjectResponse(
    val name: String,
    val path: String,
    @SerialName("last_opened") val lastOpened: String,
)

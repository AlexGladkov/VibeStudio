package studio.vibe.shared.feature.remote.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class GitInfoResponse(
    val branch: String,
    val ahead: Int,
    val behind: Int,
)

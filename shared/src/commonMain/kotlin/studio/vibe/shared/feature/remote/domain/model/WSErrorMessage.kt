package studio.vibe.shared.feature.remote.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class WSErrorMessage(
    val type: String,
    val code: String,
    val message: String,
    val fatal: Boolean,
)

package studio.vibe.shared.feature.remote.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WSRateLimitedMessage(
    val type: String,
    val message: String,
    @SerialName("retry_after_ms") val retryAfterMs: Int,
)

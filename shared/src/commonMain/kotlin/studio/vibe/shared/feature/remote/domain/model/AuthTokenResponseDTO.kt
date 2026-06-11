package studio.vibe.shared.feature.remote.domain.model

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthTokenResponseDTO(
    val token: String,
    @SerialName("expires_at") val expiresAt: Instant,
    @SerialName("device_id") val deviceId: String,
)

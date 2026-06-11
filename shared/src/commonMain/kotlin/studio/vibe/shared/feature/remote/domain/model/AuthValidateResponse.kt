package studio.vibe.shared.feature.remote.domain.model

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthValidateResponse(
    val valid: Boolean,
    @SerialName("device_id") val deviceId: String,
    @SerialName("expires_at") val expiresAt: Instant,
)

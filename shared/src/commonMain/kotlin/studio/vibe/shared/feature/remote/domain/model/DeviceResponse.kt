package studio.vibe.shared.feature.remote.domain.model

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceResponse(
    @SerialName("device_id") val deviceId: String,
    val ip: String,
    @SerialName("user_agent") val userAgent: String,
    @SerialName("connected_since") val connectedSince: Instant,
    @SerialName("last_activity") val lastActivity: Instant,
    @SerialName("attached_sessions") val attachedSessions: List<String>,
    @SerialName("is_self") val isSelf: Boolean,
)

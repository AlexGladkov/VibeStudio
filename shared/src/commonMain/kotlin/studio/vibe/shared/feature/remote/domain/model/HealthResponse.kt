package studio.vibe.shared.feature.remote.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    @SerialName("uptime_seconds") val uptimeSeconds: Int,
    @SerialName("connected_devices") val connectedDevices: Int,
    @SerialName("max_devices") val maxDevices: Int,
    val tls: String,
)

package studio.vibe.shared.feature.remote.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WSDeviceDisconnectedMessage(
    val type: String,
    @SerialName("device_id") val deviceId: String,
    val reason: String,
)

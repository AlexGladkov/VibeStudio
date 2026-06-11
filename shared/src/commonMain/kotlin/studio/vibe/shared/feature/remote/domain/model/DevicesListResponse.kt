package studio.vibe.shared.feature.remote.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DevicesListResponse(
    val devices: List<DeviceResponse>,
    @SerialName("max_devices") val maxDevices: Int,
)

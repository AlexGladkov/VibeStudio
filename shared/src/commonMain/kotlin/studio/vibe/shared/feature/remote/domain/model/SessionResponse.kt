package studio.vibe.shared.feature.remote.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SessionResponse(
    val id: String,
    val title: String,
    val state: String,
    @SerialName("is_agent") val isAgent: Boolean,
    @SerialName("has_remote_attachment") val hasRemoteAttachment: Boolean,
    @SerialName("attached_device_id") val attachedDeviceId: String? = null,
)

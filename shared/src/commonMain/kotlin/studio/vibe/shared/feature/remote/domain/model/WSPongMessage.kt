package studio.vibe.shared.feature.remote.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WSPongMessage(
    val type: String,
    val ts: Long,
    @SerialName("server_ts") val serverTs: Long,
)

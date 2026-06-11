package studio.vibe.shared.feature.remote.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WSSessionStateMessage(
    val type: String,
    @SerialName("session_id") val sessionId: String,
    val state: String,
    @SerialName("exit_code") val exitCode: Int? = null,
)

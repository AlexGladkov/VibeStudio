package studio.vibe.shared.feature.assistant.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AssistantStartResponse(
    val ok: Boolean,
    val assistant: String,
    @SerialName("session_id") val sessionId: String,
)

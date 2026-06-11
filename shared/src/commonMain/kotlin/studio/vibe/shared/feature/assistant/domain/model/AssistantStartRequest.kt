package studio.vibe.shared.feature.assistant.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AssistantStartRequest(val assistant: String? = null)

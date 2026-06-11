package studio.vibe.shared.feature.remote.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class WSInputMessage(val type: WSMessageType, val data: String)

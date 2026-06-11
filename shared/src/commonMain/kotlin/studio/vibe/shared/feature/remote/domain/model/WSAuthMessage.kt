package studio.vibe.shared.feature.remote.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class WSAuthMessage(val type: WSMessageType, val token: String)

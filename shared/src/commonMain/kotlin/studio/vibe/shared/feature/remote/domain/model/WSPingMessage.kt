package studio.vibe.shared.feature.remote.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class WSPingMessage(val type: WSMessageType, val ts: Long)

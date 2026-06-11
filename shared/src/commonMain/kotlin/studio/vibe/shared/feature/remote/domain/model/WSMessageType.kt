package studio.vibe.shared.feature.remote.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class WSMessageType {
    auth, input, resize, ping, detach
}

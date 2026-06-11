package studio.vibe.shared.feature.remote.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AuthTokenRequest(val pin: String)

package studio.vibe.shared.feature.remote.domain.model

import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class RemoteAuthorizationToken(
    val token: String,
    val device: RemoteDevice,
    val expiresAt: Instant,
)

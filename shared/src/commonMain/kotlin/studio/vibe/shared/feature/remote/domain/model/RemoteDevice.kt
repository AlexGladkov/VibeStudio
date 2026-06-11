package studio.vibe.shared.feature.remote.domain.model

import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class RemoteDevice(
    val id: Uuid,
    val displayName: String,
    val ipAddress: String,
    val connectedAt: Instant,
)

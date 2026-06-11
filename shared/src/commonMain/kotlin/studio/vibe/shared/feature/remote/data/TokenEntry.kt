package studio.vibe.shared.feature.remote.data

import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal data class TokenEntry(
    val deviceId: Uuid,
    val clientIP: String,
    val issuedAt: Instant,
    val expiresAt: Instant,
)

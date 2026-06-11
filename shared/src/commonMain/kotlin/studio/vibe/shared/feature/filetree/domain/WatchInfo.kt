package studio.vibe.shared.feature.filetree.domain

import kotlin.time.Instant
import studio.vibe.shared.core.common.FilePath
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
data class WatchInfo(
    val token: WatchToken,
    val directory: FilePath,
    val options: WatchOptions,
    val startedAt: Instant,
)

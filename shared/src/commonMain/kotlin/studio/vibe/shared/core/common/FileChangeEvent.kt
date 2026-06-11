package studio.vibe.shared.core.common

import kotlin.time.Instant

data class FileChangeEvent(
    val path: FilePath,
    val kind: FileChangeKind,
    val timestamp: Instant,
)

package studio.vibe.shared.feature.git.domain.model

import kotlin.time.Instant

data class GitCommitInfo(
    val hash: String,
    val shortHash: String,
    val message: String,
    val author: String,
    val date: Instant,
)

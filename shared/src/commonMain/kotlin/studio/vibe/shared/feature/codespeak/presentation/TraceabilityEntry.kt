package studio.vibe.shared.feature.codespeak.presentation

import studio.vibe.shared.core.common.FilePath

data class TraceabilityEntry(
    val specName: String,
    val linkedFiles: List<FilePath>,
)

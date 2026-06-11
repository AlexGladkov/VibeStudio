package studio.vibe.shared.feature.codespeak.presentation

import studio.vibe.shared.core.common.FilePath

/** Inverse of [TraceabilityEntry]: which specs reference a given file. */
data class FileTraceabilityEntry(
    val filePath: FilePath,
    val referencedBySpecs: List<String>,
)

package studio.vibe.shared.feature.codespeak.domain.usecase

import studio.vibe.shared.core.common.FilePath

/**
 * Parameters for [RunSpecBuildUseCase].
 *
 * @param command The full CLI command to execute (e.g. `["codespeak", "build"]`).
 * @param workDir The working directory in which the command runs.
 */
data class RunSpecBuildParams(
    val command: List<String>,
    val workDir: FilePath,
)

package studio.vibe.shared.feature.project.domain.usecase

import studio.vibe.shared.core.common.FilePath

data class CreateProjectParams(
    val name: String,
    val parentPath: FilePath,
)

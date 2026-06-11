package studio.vibe.shared.feature.codespeak.domain.model

import studio.vibe.shared.core.common.FilePath
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
public data class SpecFile(
    val id: Uuid = Uuid.random(),
    val path: FilePath,
    val name: String = path.path
        .substringAfterLast('/')
        .removeSuffix(".md")
        .removeSuffix(".cs"),
    val status: SpecStatus = SpecStatus.UNKNOWN,
    val passCount: Int = 0,
    val totalCount: Int = 0,
)

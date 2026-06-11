@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.codespeak.domain.model

import studio.vibe.shared.core.common.FilePath
import kotlin.uuid.Uuid

public data class GeneratedFile(
    val id: Uuid = Uuid.random(),
    val path: FilePath,
    val specName: String = "",
) {
    public val name: String get() = path.name
}

@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.core.common.project

import studio.vibe.shared.core.common.FilePath
import kotlin.uuid.Uuid

sealed class ProjectManagerError(override val message: String) : Exception(message) {
    data class InvalidPath(val path: FilePath) : ProjectManagerError("Path does not exist or is not a directory: ${path.path}")
    data class Duplicate(val existingId: Uuid, val path: FilePath) : ProjectManagerError("Project already exists at: ${path.path}")
    data class NotFound(val id: Uuid) : ProjectManagerError("Project not found: $id")
    data class PersistenceFailed(val underlying: Throwable) : ProjectManagerError("Failed to persist project list: ${underlying.message}")
    data class ProjectLimitReached(val max: Int) : ProjectManagerError("Maximum number of projects reached: $max")
}

package studio.vibe.shared.feature.session.domain.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import studio.vibe.shared.core.common.project.ProjectSessionSnapshot
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class AppSessionSnapshot(
    val version: Int,
    val capturedAt: Instant,
    val activeProjectId: Uuid? = null,
    val projectSessions: List<ProjectSessionSnapshot>,
)

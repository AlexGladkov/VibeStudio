package studio.vibe.shared.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable
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

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class ProjectSessionSnapshot(
    val projectId: Uuid,
    val terminalLayouts: List<TerminalLayoutSnapshot>,
    val scrollbackFile: FilePath? = null,
    val sidebarVisible: Boolean,
    val sidebarWidth: Double,
)

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class TerminalLayoutSnapshot(
    val sessionId: Uuid,
    val title: String,
    val splitDirection: SplitDirection? = null,
    val workingDirectory: FilePath? = null,
)

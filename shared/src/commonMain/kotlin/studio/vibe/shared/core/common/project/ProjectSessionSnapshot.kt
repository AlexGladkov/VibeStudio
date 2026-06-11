@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.core.common.project

import kotlinx.serialization.Serializable
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.core.common.terminal.TerminalLayoutSnapshot
import kotlin.uuid.Uuid

@Serializable
data class ProjectSessionSnapshot(
    val projectId: Uuid,
    val terminalLayouts: List<TerminalLayoutSnapshot>,
    val scrollbackFile: FilePath? = null,
    val sidebarVisible: Boolean,
    val sidebarWidth: Double,
)

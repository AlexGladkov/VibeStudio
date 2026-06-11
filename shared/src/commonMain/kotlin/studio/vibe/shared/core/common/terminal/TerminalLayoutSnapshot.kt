package studio.vibe.shared.core.common.terminal

import kotlinx.serialization.Serializable
import studio.vibe.shared.core.common.FilePath
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class TerminalLayoutSnapshot(
    val sessionId: Uuid,
    val title: String,
    val splitDirection: SplitDirection? = null,
    val workingDirectory: FilePath? = null,
)

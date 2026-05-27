package studio.vibe.shared.model

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
@Serializable
data class Project(
    val id: Uuid = Uuid.random(),
    val name: String,
    val path: FilePath,
    val color: HexColor? = null,
    val lastOpened: Instant = Clock.System.now(),
    val shellPath: String = "/bin/zsh",
    val productionURL: String? = null,
)

package studio.vibe.shared.model

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class FreeTab(
    val id: Uuid = Uuid.random(),
    val title: String = "Terminal",
)

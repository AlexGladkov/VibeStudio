package studio.vibe.shared.feature.toolbar.presentation

import studio.vibe.shared.core.common.AIAgent
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Per-project mutable state kept as a snapshot inside a StateFlow. */
@OptIn(ExperimentalUuidApi::class)
internal data class ToolbarProjectData(
    val selectedAgents: Map<Uuid, AIAgent> = emptyMap(),
)

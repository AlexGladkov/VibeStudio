@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.terminal.domain.usecase

import kotlin.uuid.Uuid

/**
 * Parameters for [ListTerminalSessionsUseCase].
 *
 * @param projectId The project whose sessions should be listed.
 */
data class ListTerminalSessionsParams(val projectId: Uuid)

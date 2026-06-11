@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.terminal.domain.usecase

import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.core.common.terminal.TerminalSize
import kotlin.uuid.Uuid

/**
 * Parameters for [CreateTerminalSessionUseCase].
 *
 * @param projectId        The project the session belongs to.
 * @param shell            Shell executable path (null = platform default).
 * @param workingDirectory Working directory for the new shell process.
 * @param size             Initial terminal dimensions.
 */
data class CreateTerminalSessionParams(
    val projectId: Uuid,
    val shell: String?,
    val workingDirectory: FilePath?,
    val size: TerminalSize = TerminalSize(columns = 80, rows = 24),
)

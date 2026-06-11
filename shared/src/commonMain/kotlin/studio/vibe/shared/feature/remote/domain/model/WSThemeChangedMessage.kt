package studio.vibe.shared.feature.remote.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import studio.vibe.shared.core.common.terminal.TerminalColorsResponse

@Serializable
data class WSThemeChangedMessage(
    val type: String,
    val appearance: String,
    @SerialName("terminal_colors") val terminalColors: TerminalColorsResponse,
)

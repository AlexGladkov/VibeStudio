package studio.vibe.shared.core.common.terminal

import kotlinx.serialization.Serializable

@Serializable
data class TerminalColorsResponse(
    val foreground: String,
    val background: String,
    val cursor: String,
    val selection: String,
    val ansi: List<String>,
)

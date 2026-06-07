package studio.vibe.desktop.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import studio.vibe.desktop.ui.theme.DSColors
import studio.vibe.desktop.ui.theme.LocalDSColors

/**
 * Resolves the brand [Color] for an agent identified by [agentId].
 *
 * Agent ids match [AIAgent.id] constants defined in [BuiltInAgents].
 * Unknown agents fall back to [DSColors.textMuted].
 */
@Composable
fun agentBrandColor(agentId: String): Color {
    val colors = LocalDSColors.current
    return agentBrandColor(agentId, colors)
}

fun agentBrandColor(agentId: String, colors: DSColors): Color = when (agentId) {
    "claude" -> colors.agentClaude
    "opencode" -> colors.agentOpenCode
    "codex" -> colors.agentCodex
    "gemini" -> colors.agentGemini
    "qwenCode" -> colors.agentQwen
    "codeSpeak" -> colors.agentCodeSpeak
    else -> colors.textMuted
}

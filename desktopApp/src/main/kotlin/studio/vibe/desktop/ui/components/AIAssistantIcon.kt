package studio.vibe.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSColors
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.shared.core.common.AIAgent

/**
 * Displays a branded icon for an AI agent.
 *
 * Port of SwiftUI `AIAssistantIconView.swift`.
 * Each agent is represented by a distinct color circle with an initial derived
 * from its id — built-in agents have dedicated palette entries; plugin agents
 * fall back to [DSColors.textSecondary] with the first letter of [AIAgent.displayName].
 *
 * @param agent The AI agent whose icon to render.
 * @param size  Diameter of the icon in dp (default 16).
 */
@Composable
fun AIAssistantIcon(
    agent: AIAgent,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    val (bgColor, label) = agentStyle(agent, LocalDSColors.current)
    val fontSize = (size.value * 0.5f).sp

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
                color = Color.White,
            ),
        )
    }
}

/**
 * Id-based style lookup.  Built-in agents have dedicated palette entries;
 * plugin agents fall back to [DSColors.textSecondary] with a computed monogram.
 */
private fun agentStyle(agent: AIAgent, colors: DSColors): Pair<Color, String> = when (agent.id) {
    "claude"    -> colors.agentClaude    to "C"
    "opencode"  -> colors.agentOpenCode  to "O"
    "codex"     -> colors.agentCodex     to "X"
    "gemini"    -> colors.agentGemini    to "G"
    "qwenCode"  -> colors.agentQwen      to "Q"
    "codeSpeak" -> colors.agentCodeSpeak to "S"
    else -> colors.textSecondary to (agent.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?")
}

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
import studio.vibe.shared.model.AIAssistant

/**
 * Displays a branded icon for an AI assistant.
 *
 * Port of SwiftUI `AIAssistantIconView.swift`.
 * Each assistant is represented by a distinct color circle with an initial or
 * short monogram — matching the brand colors defined in [DSColor].
 *
 * @param assistant The AI assistant whose icon to render.
 * @param size      Diameter of the icon in dp (default 16).
 */
@Composable
fun AIAssistantIcon(
    assistant: AIAssistant,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    val (bgColor, label) = assistantStyle(assistant, LocalDSColors.current)
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

private fun assistantStyle(assistant: AIAssistant, colors: DSColors): Pair<Color, String> = when (assistant) {
    AIAssistant.CLAUDE     -> colors.agentClaude     to "C"
    AIAssistant.OPENCODE   -> colors.agentOpenCode   to "O"
    AIAssistant.CODEX      -> colors.agentCodex      to "X"
    AIAssistant.GEMINI     -> colors.agentGemini     to "G"
    AIAssistant.QWEN_CODE  -> colors.agentQwen       to "Q"
    AIAssistant.CODE_SPEAK -> colors.agentCodeSpeak  to "S"
}

package studio.vibe.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing

/**
 * Compact status badge — small pill with colored text and translucent background.
 *
 * Port of SwiftUI `StatusBadgeView.swift`.
 *
 * Usage:
 * ```kotlin
 * StatusBadge("STAGED", color = DSColor.accentPrimary)
 * StatusBadge("PASS",   color = DSColor.gitAdded)
 * StatusBadge("FAIL",   color = DSColor.gitDeleted)
 * ```
 */
@Composable
fun StatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = DSFont.badgeSmall,
        color = color,
        modifier = modifier
            .background(
                color = color.copy(alpha = 0.15f),
                shape = RoundedCornerShape(DSRadius.sm),
            )
            .padding(horizontal = DSSpacing.xs, vertical = DSSpacing.xxs),
    )
}

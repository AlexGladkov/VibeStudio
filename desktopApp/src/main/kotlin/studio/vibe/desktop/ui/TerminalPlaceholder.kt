package studio.vibe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSSpacing

@Composable
fun TerminalPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(DSColor.surfaceBase),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "\$ _",
                fontFamily = FontFamily.Monospace,
                fontSize = 24.sp,
                color = DSColor.textMuted,
            )
            Spacer(Modifier.height(DSSpacing.md))
            Text(
                text = "Terminal area",
                style = DSFont.sidebarItem,
                color = DSColor.textSecondary,
            )
            Spacer(Modifier.height(DSSpacing.xs))
            Text(
                text = "pty4j integration pending",
                style = DSFont.sidebarItemSmall,
                color = DSColor.textMuted,
                modifier = Modifier.padding(horizontal = DSSpacing.lg),
            )
        }
    }
}

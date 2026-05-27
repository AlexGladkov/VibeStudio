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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.vibe.desktop.ui.theme.ColorBackground
import studio.vibe.desktop.ui.theme.ColorTextSecondary

/**
 * Placeholder for the terminal area.
 *
 * pty4j integration is tracked separately. This composable occupies the correct
 * position in the layout so all surrounding UI (toolbar, sidebar, git panel) can
 * be developed and tested without a running terminal.
 */
@Composable
fun TerminalPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(ColorBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "\$ _",
                fontFamily = FontFamily.Monospace,
                fontSize = 24.sp,
                color = ColorTextSecondary.copy(alpha = 0.4f),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Terminal area",
                fontSize = 13.sp,
                color = ColorTextSecondary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "pty4j integration pending",
                fontSize = 11.sp,
                color = ColorTextSecondary.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

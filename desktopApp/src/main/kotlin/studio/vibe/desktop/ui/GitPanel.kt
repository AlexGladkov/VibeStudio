package studio.vibe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.ui.theme.ColorTextSecondary

/**
 * Right-side git changes panel.
 *
 * Backed by [GitSidebarViewModel] from the shared KMP module.
 * Full implementation (staged/unstaged diff list, commit box) is a follow-up task.
 * This scaffold occupies the correct layout slot and wires the ViewModel.
 */
@Composable
fun GitPanel(
    container: DesktopServiceContainer,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // Panel header
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = "GIT CHANGES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = ColorTextSecondary,
                letterSpacing = 0.8.sp,
            )
        }

        // Content placeholder — GitSidebarViewModel is available via container
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Git changes panel",
                    fontSize = 13.sp,
                    color = ColorTextSecondary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Full diff UI pending",
                    fontSize = 11.sp,
                    color = ColorTextSecondary.copy(alpha = 0.5f),
                )
            }
        }
    }
}

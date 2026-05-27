package studio.vibe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSLayout
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing

@Composable
fun GitPanel(
    container: DesktopServiceContainer,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.background(DSColor.surfaceRaised)) {
        // Header: "CHANGES" + count badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DSLayout.gitSectionHeaderHeight)
                .padding(horizontal = DSLayout.sidebarHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "CHANGES",
                style = DSFont.sidebarSection,
                color = DSColor.textSecondary,
            )
            // Count badge
            Box(
                modifier = Modifier
                    .background(DSColor.accentPrimary, RoundedCornerShape(50))
                    .padding(horizontal = DSSpacing.xs, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "0",
                    style = DSFont.badgeSmall,
                    color = DSColor.textInverse,
                )
            }
        }

        // 1px divider
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DSColor.borderDefault),
        )

        // Empty state
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = DSColor.textMuted,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.height(DSSpacing.sm))
                Text(
                    text = "Working tree clean",
                    style = DSFont.sidebarItem,
                    color = DSColor.textMuted,
                )
            }
        }
    }
}

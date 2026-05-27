package studio.vibe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSLayout
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing

@Composable
fun WelcomeView(
    onOpenProject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DSColor.surfaceBase),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.widthIn(max = 420.dp).padding(32.dp),
        ) {
            // Hero icon
            Icon(
                Icons.Default.Terminal,
                contentDescription = null,
                tint = DSColor.accentPrimary,
                modifier = Modifier.size(48.dp),
            )

            Spacer(Modifier.height(DSSpacing.xl))

            // Title
            Text(
                text = "VibeStudio",
                style = DSFont.welcomeTitle,
                color = DSColor.textPrimary,
            )

            Spacer(Modifier.height(DSSpacing.sm))

            // Subtitle
            Text(
                text = "Open a folder to get started",
                style = DSFont.sidebarItem,
                color = DSColor.textSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(DSSpacing.xl))

            // Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
                // Primary: Open Folder
                Button(
                    onClick = onOpenProject,
                    shape = RoundedCornerShape(DSRadius.md),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DSColor.buttonPrimaryBg,
                        contentColor = DSColor.buttonPrimaryText,
                    ),
                    modifier = Modifier.height(28.dp),
                ) {
                    Text(
                        text = "Open Folder",
                        style = DSFont.buttonLabel,
                        color = DSColor.buttonPrimaryText,
                    )
                }

                // Secondary: Create New
                OutlinedButton(
                    onClick = { /* TODO */ },
                    shape = RoundedCornerShape(DSRadius.md),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DSColor.borderDefault),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = DSColor.surfaceOverlay,
                        contentColor = DSColor.textPrimary,
                    ),
                    modifier = Modifier.height(28.dp),
                ) {
                    Text(
                        text = "Create New",
                        style = DSFont.buttonLabel,
                        color = DSColor.textPrimary,
                    )
                }
            }
        }
    }
}

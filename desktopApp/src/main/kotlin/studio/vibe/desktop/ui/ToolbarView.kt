@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.ui.theme.ColorAccentBlue
import studio.vibe.desktop.ui.theme.ColorAccentRed
import studio.vibe.desktop.ui.theme.ColorBorder
import studio.vibe.desktop.ui.theme.ColorTextSecondary
import studio.vibe.shared.model.AIAssistant

/**
 * Top toolbar:
 *   [spacer] [agent picker dropdown] [play/stop button] [settings icon]
 *
 * Mirrors the macOS ToolbarView right-side layout: agent selector + run controls + settings.
 */
@Composable
fun ToolbarView(container: DesktopServiceContainer) {
    val toolbarState by container.toolbarViewModel.state.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = ColorBorder,
                shape = RoundedCornerShape(0.dp),
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        Spacer(Modifier.weight(1f))

        // Agent picker
        AgentPickerButton(
            selectedAssistant = toolbarState.selectedAssistant,
            onSelectAssistant = { container.toolbarViewModel.selectAssistant(it) },
        )

        Spacer(Modifier.width(8.dp))

        // Play / Stop button
        if (toolbarState.isAgentRunning) {
            IconButton(
                onClick = { container.toolbarViewModel.stopAgent() },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop agent",
                    tint = ColorAccentRed,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            IconButton(
                onClick = { container.toolbarViewModel.launchAgent() },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Launch agent",
                    tint = ColorAccentBlue,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(Modifier.width(4.dp))

        // Settings
        IconButton(
            onClick = { /* TODO: open settings panel */ },
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = ColorTextSecondary,
                modifier = Modifier.size(16.dp),
            )
        }

        // Error snackbar hint (non-blocking inline display)
        toolbarState.errorMessage?.let { msg ->
            Spacer(Modifier.width(8.dp))
            Text(
                text = msg,
                fontSize = 11.sp,
                color = ColorAccentRed,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

// ── Agent picker ──────────────────────────────────────────────────────────────

@Composable
private fun AgentPickerButton(
    selectedAssistant: AIAssistant,
    onSelectAssistant: (AIAssistant) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, ColorBorder, RoundedCornerShape(4.dp))
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = selectedAssistant.displayName,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AIAssistant.entries.forEach { assistant ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = assistant.displayName,
                            fontSize = 13.sp,
                        )
                    },
                    onClick = {
                        onSelectAssistant(assistant)
                        expanded = false
                    },
                )
            }
        }
    }
}

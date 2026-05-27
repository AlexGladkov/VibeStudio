@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSLayout
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.shared.model.AIAssistant

@Composable
fun ToolbarView(container: DesktopServiceContainer) {
    val toolbarState by container.toolbarViewModel.state.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DSLayout.toolbarHeight)
            .background(DSColor.surfaceToolbar)
            .padding(horizontal = DSSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))

        // Agent picker
        AgentPickerButton(
            selectedAssistant = toolbarState.selectedAssistant,
            onSelectAssistant = { container.toolbarViewModel.selectAssistant(it) },
        )

        Spacer(Modifier.width(DSSpacing.sm))

        // Play / Stop
        Box(
            modifier = Modifier
                .size(DSLayout.toolbarIconButtonWidth, DSLayout.toolbarButtonHeight)
                .clickable {
                    if (toolbarState.isAgentRunning) container.toolbarViewModel.stopAgent()
                    else container.toolbarViewModel.launchAgent()
                },
            contentAlignment = Alignment.Center,
        ) {
            if (toolbarState.isAgentRunning) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = "Stop agent",
                    tint = DSColor.actionStop,
                    modifier = Modifier.size(13.dp),
                )
            } else {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Launch agent",
                    tint = DSColor.actionRun,
                    modifier = Modifier.size(13.dp),
                )
            }
        }

        Spacer(Modifier.width(DSSpacing.sm))

        // Settings
        Box(
            modifier = Modifier
                .size(DSLayout.toolbarIconButtonWidth, DSLayout.toolbarButtonHeight)
                .clickable { /* TODO: open settings */ },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Settings",
                tint = DSColor.textSecondary,
                modifier = Modifier.size(DSFont.iconBase.value.dp),
            )
        }

        // Error inline
        toolbarState.errorMessage?.let { msg ->
            Spacer(Modifier.width(DSSpacing.sm))
            Text(
                text = msg,
                style = DSFont.sidebarItemSmall,
                color = DSColor.gitDeleted,
            )
        }
    }
}

// ── Agent picker ─────────────────────────────────────────────────────────────

@Composable
private fun AgentPickerButton(
    selectedAssistant: AIAssistant,
    onSelectAssistant: (AIAssistant) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(DSRadius.md))
                .background(DSColor.toolbarControlBg)
                .border(1.dp, DSColor.toolbarControlBorder, RoundedCornerShape(DSRadius.md))
                .clickable { expanded = true }
                .padding(horizontal = DSSpacing.sm, vertical = DSSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Agent icon placeholder — colored dot per agent
            Box(
                Modifier
                    .size(DSFont.iconLG.value.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(agentColor(selectedAssistant)),
            )
            Spacer(Modifier.width(DSSpacing.xs))
            Text(
                text = selectedAssistant.displayName,
                style = DSFont.tabTitle,
                color = DSColor.textPrimary,
            )
            Spacer(Modifier.width(DSSpacing.xxs))
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = DSColor.textSecondary,
                modifier = Modifier.size(DSFont.iconSM.value.dp),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AIAssistant.entries.forEach { assistant ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(agentColor(assistant)),
                            )
                            Spacer(Modifier.width(DSSpacing.sm))
                            Text(
                                text = assistant.displayName,
                                style = DSFont.sidebarItem,
                                color = DSColor.textPrimary,
                            )
                        }
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

private fun agentColor(assistant: AIAssistant): androidx.compose.ui.graphics.Color = when (assistant) {
    AIAssistant.CLAUDE -> DSColor.agentClaude
    AIAssistant.OPENCODE -> DSColor.agentOpenCode
    AIAssistant.CODEX -> DSColor.agentCodex
    AIAssistant.GEMINI -> DSColor.agentGemini
    AIAssistant.QWEN_CODE -> DSColor.agentQwen
    AIAssistant.CODE_SPEAK -> DSColor.agentCodeSpeak
}

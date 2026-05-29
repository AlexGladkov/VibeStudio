@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSColors
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSLayout
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.shared.contract.AgentAvailabilityStatus
import studio.vibe.shared.model.AIAssistant

// ── ToolbarView ───────────────────────────────────────────────────────────────

/**
 * Android Studio-style run-configuration toolbar.
 *
 * Layout (trailing-aligned):
 * `Spacer  [AgentPicker ▼]  [▶/■]  [CS toggle]  [⚙️]  [error?]`
 */
@Composable
fun ToolbarView(
    container: DesktopServiceContainer,
    isCodeSpeakMode: Boolean = false,
    onOpenSettings: () -> Unit = {},
    onToggleCodeSpeakMode: () -> Unit = {},
    onInstallAgent: (AIAssistant) -> Unit = {},
) {
    val toolbarState by container.toolbarViewModel.state.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DSLayout.toolbarHeight)
            .background(LocalDSColors.current.surfaceToolbar)
            .padding(horizontal = DSSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))

        // Agent picker with availability status
        AgentPickerButton(
            selectedAssistant = toolbarState.selectedAssistant,
            availabilityMap = toolbarState.agentAvailability,
            isRunning = toolbarState.isAgentRunning,
            onSelectAssistant = { container.toolbarViewModel.selectAssistant(it) },
            onInstallAgent = onInstallAgent,
        )

        Spacer(Modifier.width(DSSpacing.sm))

        // Play / Stop
        PlayStopButton(
            isRunning = toolbarState.isAgentRunning,
            onPlay = { container.toolbarViewModel.launchAgent() },
            onStop = { container.toolbarViewModel.stopAgent() },
        )

        Spacer(Modifier.width(DSSpacing.sm))

        // CodeSpeak mode toggle
        CodeSpeakToggleButton(
            isActive = isCodeSpeakMode,
            onClick = onToggleCodeSpeakMode,
        )

        Spacer(Modifier.width(DSSpacing.sm))

        // Settings gear
        SettingsButton(onClick = onOpenSettings)

        // Inline error message (trailing)
        toolbarState.errorMessage?.let { msg ->
            Spacer(Modifier.width(DSSpacing.sm))
            Text(
                text = msg,
                style = DSFont.sidebarItemSmall,
                color = LocalDSColors.current.gitDeleted,
            )
        }
    }
}

// ── Agent Picker ──────────────────────────────────────────────────────────────

/**
 * Dropdown button showing the selected agent with its availability dot.
 *
 * The dropdown splits agents into two groups separated by a divider:
 * 1. Available agents (canLaunch = true)
 * 2. Unavailable / not-installed agents
 *
 * Not-installed agents show "Click to install" hint and trigger [onInstallAgent]
 * instead of selecting.
 */
@Composable
private fun AgentPickerButton(
    selectedAssistant: AIAssistant,
    availabilityMap: Map<AIAssistant, AgentAvailabilityStatus>,
    isRunning: Boolean,
    onSelectAssistant: (AIAssistant) -> Unit,
    onInstallAgent: (AIAssistant) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val available = AIAssistant.entries.filter { assistant ->
        val status = availabilityMap[assistant]
        status is AgentAvailabilityStatus.Available
    }
    val unavailable = AIAssistant.entries.filter { assistant ->
        val status = availabilityMap[assistant]
        status == null || status is AgentAvailabilityStatus.NotInstalled || status is AgentAvailabilityStatus.Checking
    }

    Box {
        // Trigger button
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(DSRadius.md))
                .background(LocalDSColors.current.toolbarControlBg)
                .border(1.dp, LocalDSColors.current.toolbarControlBorder, RoundedCornerShape(DSRadius.md))
                .clickable(enabled = !isRunning) { expanded = true }
                .padding(horizontal = DSSpacing.sm, vertical = DSSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Agent color square
            Box(
                Modifier
                    .size(DSFont.iconLG.value.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(agentColor(selectedAssistant, LocalDSColors.current)),
            )
            Spacer(Modifier.width(DSSpacing.xs))
            Text(
                text = selectedAssistant.displayName,
                style = DSFont.tabTitle,
                color = if (isRunning) LocalDSColors.current.textMuted else LocalDSColors.current.textPrimary,
            )
            Spacer(Modifier.width(DSSpacing.xxs))
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = if (isRunning) LocalDSColors.current.textMuted else LocalDSColors.current.textSecondary,
                modifier = Modifier.size(DSFont.iconSM.value.dp),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            // Available agents
            available.forEach { assistant ->
                val status = availabilityMap[assistant]
                AgentDropdownItem(
                    assistant = assistant,
                    status = status,
                    isSelected = assistant == selectedAssistant,
                    onClick = {
                        onSelectAssistant(assistant)
                        expanded = false
                    },
                    onInstall = null,
                )
            }

            // Divider only when both groups are non-empty
            if (available.isNotEmpty() && unavailable.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = DSSpacing.xs),
                    color = LocalDSColors.current.borderDefault,
                )
            }

            // Unavailable agents
            unavailable.forEach { assistant ->
                val status = availabilityMap[assistant]
                val isNotInstalled = status is AgentAvailabilityStatus.NotInstalled || status == null
                AgentDropdownItem(
                    assistant = assistant,
                    status = status,
                    isSelected = assistant == selectedAssistant,
                    onClick = {
                        if (isNotInstalled) {
                            expanded = false
                            onInstallAgent(assistant)
                        } else {
                            onSelectAssistant(assistant)
                            expanded = false
                        }
                    },
                    onInstall = if (isNotInstalled) {
                        {
                            expanded = false
                            onInstallAgent(assistant)
                        }
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun AgentDropdownItem(
    assistant: AIAssistant,
    status: AgentAvailabilityStatus?,
    isSelected: Boolean,
    onClick: () -> Unit,
    onInstall: (() -> Unit)?,
) {
    val isNotInstalled = status is AgentAvailabilityStatus.NotInstalled || status == null
    val isChecking = status is AgentAvailabilityStatus.Checking
    val hasApiKeyWarning = status is AgentAvailabilityStatus.Available &&
        !status.hasAPIKey &&
        assistant.apiKeyEnvironmentVariable != null

    val dotColor: Color = when {
        isChecking -> LocalDSColors.current.indicatorWaiting
        isNotInstalled -> LocalDSColors.current.textMuted
        hasApiKeyWarning -> LocalDSColors.current.indicatorWaiting
        else -> LocalDSColors.current.indicatorRunning
    }

    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Availability dot
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Spacer(Modifier.width(DSSpacing.sm))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Agent color square
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(agentColor(assistant, LocalDSColors.current)),
                        )
                        Spacer(Modifier.width(DSSpacing.xs))
                        Text(
                            text = assistant.displayName,
                            style = DSFont.sidebarItem,
                            color = if (isNotInstalled) LocalDSColors.current.textSecondary else LocalDSColors.current.textPrimary,
                        )
                    }

                    // Sub-label: install hint or api key warning
                    when {
                        isNotInstalled -> Text(
                            text = "Click to install",
                            style = DSFont.sidebarItemSmall,
                            color = LocalDSColors.current.accentPrimary,
                        )
                        hasApiKeyWarning -> Text(
                            text = "API key not set",
                            style = DSFont.sidebarItemSmall,
                            color = LocalDSColors.current.indicatorWaiting,
                        )
                    }
                }

                Spacer(Modifier.width(DSSpacing.sm))

                // Trailing indicator
                when {
                    isSelected && !isNotInstalled -> Text(
                        text = "✓",
                        style = DSFont.smallButtonLabel,
                        color = LocalDSColors.current.accentPrimary,
                    )
                    isNotInstalled -> Icon(
                        imageVector = Icons.Default.KeyboardArrowDown, // placeholder — no download icon in M3 default set
                        contentDescription = "Not installed",
                        tint = LocalDSColors.current.accentPrimary,
                        modifier = Modifier.size(DSFont.iconMD.value.dp),
                    )
                }
            }
        },
        onClick = onClick,
    )
}

// ── Play / Stop Button ────────────────────────────────────────────────────────

@Composable
private fun PlayStopButton(
    isRunning: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(DSLayout.toolbarIconButtonWidth, DSLayout.toolbarButtonHeight)
            .clickable { if (isRunning) onStop() else onPlay() },
        contentAlignment = Alignment.Center,
    ) {
        if (isRunning) {
            Icon(
                Icons.Default.Stop,
                contentDescription = "Stop agent",
                tint = LocalDSColors.current.actionStop,
                modifier = Modifier.size(13.dp),
            )
        } else {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Launch agent",
                tint = LocalDSColors.current.actionRun,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

// ── CodeSpeak Toggle ──────────────────────────────────────────────────────────

/**
 * Toggle button that switches the app between Regular and CodeSpeak modes.
 * Active state: [LocalDSColors.current.accentPrimary] background pill.
 */
@Composable
private fun CodeSpeakToggleButton(
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(DSLayout.toolbarButtonHeight)
            .clip(RoundedCornerShape(DSRadius.md))
            .background(if (isActive) LocalDSColors.current.accentPrimary.copy(alpha = 0.18f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (isActive) LocalDSColors.current.accentPrimary.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(DSRadius.md),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = DSSpacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Code,
            contentDescription = if (isActive) "Exit CodeSpeak mode" else "Enter CodeSpeak mode",
            tint = if (isActive) LocalDSColors.current.accentPrimary else LocalDSColors.current.textSecondary,
            modifier = Modifier.size(DSFont.iconBase.value.dp),
        )
    }
}

// ── Settings Button ───────────────────────────────────────────────────────────

@Composable
private fun SettingsButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(DSLayout.toolbarIconButtonWidth, DSLayout.toolbarButtonHeight)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Settings,
            contentDescription = "Settings",
            tint = LocalDSColors.current.textSecondary,
            modifier = Modifier.size(DSFont.iconBase.value.dp),
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

internal fun agentColor(assistant: AIAssistant, colors: DSColors): Color = when (assistant) {
    AIAssistant.CLAUDE -> colors.agentClaude
    AIAssistant.OPENCODE -> colors.agentOpenCode
    AIAssistant.CODEX -> colors.agentCodex
    AIAssistant.GEMINI -> colors.agentGemini
    AIAssistant.QWEN_CODE -> colors.agentQwen
    AIAssistant.CODE_SPEAK -> colors.agentCodeSpeak
}

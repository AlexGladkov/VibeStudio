@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
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
import androidx.compose.material.icons.filled.Wifi
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
import studio.vibe.desktop.remote.RemoteControlServer
import studio.vibe.shared.viewmodel.ToolbarViewModel
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSColors
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSLayout
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.shared.contract.AgentAvailabilityStatus
import studio.vibe.shared.contract.AIAgent
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
    toolbarViewModel: ToolbarViewModel,
    remoteControlServer: RemoteControlServer,
    isCodeSpeakMode: Boolean = false,
    onOpenSettings: () -> Unit = {},
    onToggleCodeSpeakMode: () -> Unit = {},
    onInstallAgent: (AIAgent) -> Unit = {},
) {
    val toolbarState by toolbarViewModel.state.collectAsState()

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
            selectedAgent = toolbarState.selectedAgent,
            availabilityMap = toolbarState.agentAvailability,
            isRunning = toolbarState.isAgentRunning,
            onSelectAgent = { toolbarViewModel.selectAgent(it) },
            onInstallAgent = onInstallAgent,
        )

        Spacer(Modifier.width(DSSpacing.sm))

        // Play / Stop
        PlayStopButton(
            isRunning = toolbarState.isAgentRunning,
            onPlay = { toolbarViewModel.launchAgent() },
            onStop = { toolbarViewModel.stopAgent() },
        )

        Spacer(Modifier.width(DSSpacing.sm))

        // Remote control status badge — shown only when the server is running.
        RemoteControlBadge(server = remoteControlServer)

        // Settings gear
        // CodeSpeak mode toggle intentionally not rendered here — the macOS
        // Swift toolbar does not expose it either; the mode is reachable via
        // ⌘⇧C and the View menu instead.
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
    selectedAgent: AIAgent?,
    availabilityMap: Map<AIAgent, AgentAvailabilityStatus>,
    isRunning: Boolean,
    onSelectAgent: (AIAgent) -> Unit,
    onInstallAgent: (AIAgent) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val knownAgents = availabilityMap.keys.toList()
    val available = knownAgents.filter { agent ->
        availabilityMap[agent] is AgentAvailabilityStatus.Available
    }
    val unavailable = knownAgents.filter { agent ->
        val status = availabilityMap[agent]
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
                    .background(agentColor(selectedAgent, LocalDSColors.current)),
            )
            Spacer(Modifier.width(DSSpacing.xs))
            Text(
                text = selectedAgent?.displayName ?: "—",
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
            available.forEach { agent ->
                val status = availabilityMap[agent]
                AgentDropdownItem(
                    agent = agent,
                    status = status,
                    isSelected = agent == selectedAgent,
                    onClick = {
                        onSelectAgent(agent)
                        expanded = false
                    },
                    onInstall = null,
                )
            }
            if (available.isNotEmpty() && unavailable.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = DSSpacing.xs),
                    color = LocalDSColors.current.borderDefault,
                )
            }
            unavailable.forEach { agent ->
                val status = availabilityMap[agent]
                val isNotInstalled = status is AgentAvailabilityStatus.NotInstalled || status == null
                AgentDropdownItem(
                    agent = agent,
                    status = status,
                    isSelected = agent == selectedAgent,
                    onClick = {
                        if (isNotInstalled) {
                            expanded = false
                            onInstallAgent(agent)
                        } else {
                            onSelectAgent(agent)
                            expanded = false
                        }
                    },
                    onInstall = if (isNotInstalled) {
                        {
                            expanded = false
                            onInstallAgent(agent)
                        }
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun AgentDropdownItem(
    agent: AIAgent,
    status: AgentAvailabilityStatus?,
    isSelected: Boolean,
    onClick: () -> Unit,
    onInstall: (() -> Unit)?,
) {
    val isNotInstalled = status is AgentAvailabilityStatus.NotInstalled || status == null
    val isChecking = status is AgentAvailabilityStatus.Checking
    val hasApiKeyWarning = status is AgentAvailabilityStatus.Available &&
        !status.hasAPIKey &&
        agent.apiKeyEnvironmentVariable != null

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
                                .background(agentColor(agent, LocalDSColors.current)),
                        )
                        Spacer(Modifier.width(DSSpacing.xs))
                        Text(
                            text = agent.displayName,
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

// ── Remote Control Badge ──────────────────────────────────────────────────────

/**
 * Compact remote-control status surface for the toolbar.
 *
 * Visible only while [studio.vibe.desktop.remote.RemoteControlServer.isRunning]
 * emits `true`. Shows a Wi-Fi icon decorated with a small device-count badge
 * (when at least one mobile client is paired) and opens a popover with the
 * current PIN, the LAN URL, the optional ngrok URL, and a Copy action — a
 * functional subset of the macOS Swift toolbar's QR popover. Generating the
 * QR image itself requires a Compose-Desktop friendly QR library; for now
 * the user can copy the URL and scan via any QR generator if needed.
 */
@Composable
private fun RemoteControlBadge(server: RemoteControlServer) {
    val isRunning by server.isRunning.collectAsState()
    if (!isRunning) return

    val devices by server.authService.connectedDevices.collectAsState()
    var showPopover by remember { mutableStateOf(false) }
    val colors = LocalDSColors.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(DSRadius.md))
                .background(colors.toolbarControlBg)
                .border(1.dp, colors.toolbarControlBorder, RoundedCornerShape(DSRadius.md))
                .clickable { showPopover = true }
                .padding(horizontal = DSSpacing.xs, vertical = DSSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.Wifi,
                contentDescription = "Remote control",
                tint = colors.gitAdded,
                modifier = Modifier.size(DSFont.iconLG.value.dp),
            )
            if (devices.isNotEmpty()) {
                Spacer(Modifier.width(DSSpacing.xxs))
                Text(
                    text = devices.size.toString(),
                    style = DSFont.statusBadge,
                    color = colors.textPrimary,
                )
            }
        }

        if (showPopover) {
            val lanIP = java.net.InetAddress.getLocalHost().hostAddress
            val pin = server.currentPin
            val ngrok = server.ngrokTunnelURL
            val lanUrl = "http://$lanIP:${server.port + 1}/?pin=$pin"
            val primaryUrl = ngrok?.let { "$it/?pin=$pin" } ?: lanUrl

            androidx.compose.ui.window.Popup(
                onDismissRequest = { showPopover = false },
                properties = androidx.compose.ui.window.PopupProperties(focusable = true),
                alignment = Alignment.TopEnd,
                offset = androidx.compose.ui.unit.IntOffset(0, DSLayout.toolbarHeight.value.toInt() + 4),
            ) {
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(DSRadius.md),
                    color = colors.surfaceOverlay,
                    shadowElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderDefault),
                ) {
                    Column(
                        modifier = Modifier
                            .width(320.dp)
                            .padding(DSSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
                    ) {
                        Text(text = "Remote Control", style = DSFont.gitBranch, color = colors.textPrimary)
                        Text(text = "PIN: $pin", style = DSFont.monoPath, color = colors.textSecondary)
                        Text(text = primaryUrl, style = DSFont.monoSmall, color = colors.accentPrimary, maxLines = 2)
                        if (devices.isEmpty()) {
                            Text(
                                text = "No devices connected",
                                style = DSFont.sidebarItemSmall,
                                color = colors.textMuted,
                            )
                        } else {
                            Text(
                                text = "${devices.size} device${if (devices.size == 1) "" else "s"} connected",
                                style = DSFont.sidebarItemSmall,
                                color = colors.gitAdded,
                            )
                        }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(DSRadius.sm))
                                .background(colors.buttonPrimaryBg)
                                .clickable {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(primaryUrl))
                                }
                                .padding(horizontal = DSSpacing.md, vertical = DSSpacing.xs),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = "Copy URL",
                                style = DSFont.smallButtonLabel,
                                color = colors.buttonPrimaryText,
                            )
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.width(DSSpacing.sm))
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Visual brand colour for an agent.  Built-in agents have a dedicated palette;
 * plugin agents fall back to [DSColors.textSecondary] so they still render
 * without crashing.  Adding a new built-in colour means extending this map only.
 */
internal fun agentColor(agent: AIAgent?, colors: DSColors): Color {
    if (agent == null) return colors.textSecondary
    return when (agent.id) {
        "claude" -> colors.agentClaude
        "opencode" -> colors.agentOpenCode
        "codex" -> colors.agentCodex
        "gemini" -> colors.agentGemini
        "qwenCode" -> colors.agentQwen
        "codeSpeak" -> colors.agentCodeSpeak
        else -> colors.textSecondary
    }
}

/** Backwards-compatible colour lookup for code paths still typed on [AIAssistant]. */
internal fun agentColor(assistant: AIAssistant?, colors: DSColors): Color = when (assistant) {
    AIAssistant.CLAUDE -> colors.agentClaude
    AIAssistant.OPENCODE -> colors.agentOpenCode
    AIAssistant.CODEX -> colors.agentCodex
    AIAssistant.GEMINI -> colors.agentGemini
    AIAssistant.QWEN_CODE -> colors.agentQwen
    AIAssistant.CODE_SPEAK -> colors.agentCodeSpeak
    null -> colors.textSecondary
}

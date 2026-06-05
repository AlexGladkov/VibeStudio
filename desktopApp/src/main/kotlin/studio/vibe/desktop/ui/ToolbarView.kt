@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.ViewSidebar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import studio.vibe.desktop.remote.RemoteControlServer
import studio.vibe.shared.contract.AgentAvailabilityStatus
import studio.vibe.shared.contract.AIAgent
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.coordinator.AppNavigationCoordinator
import studio.vibe.shared.model.CodeSpeakCommand
import studio.vibe.shared.viewmodel.ToolbarViewModel
import studio.vibe.desktop.ui.theme.DSColors
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSLayout
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.desktop.ui.theme.LocalDSColors
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage
import java.net.InetAddress
import java.net.URI

// ── ToolbarView ───────────────────────────────────────────────────────────────

/**
 * Main toolbar. Branches on [isCodeSpeakMode]:
 *  - Regular mode: Spacer → agentPicker → play/stop → openInBrowser → remoteQR
 *    → remoteIndicator → settings → changesToggle
 *  - CodeSpeak mode: three-section layout mirroring Swift's
 *    `codeSpeakThreeSectionToolbar()`.
 */
@Composable
fun ToolbarView(
    toolbarViewModel: ToolbarViewModel,
    remoteControlServer: RemoteControlServer,
    projectStore: ProjectManaging? = null,
    navigationCoordinator: AppNavigationCoordinator? = null,
    isCodeSpeakMode: Boolean = false,
    showingChangesPanel: Boolean = false,
    onOpenSettings: () -> Unit = {},
    onToggleCodeSpeakMode: () -> Unit = {},
    onToggleChangesPanel: () -> Unit = {},
    onInstallAgent: (AIAgent) -> Unit = {},
) {
    val toolbarState by toolbarViewModel.state.collectAsState()
    val activeProjectId by (projectStore?.activeProjectId?.collectAsState()
        ?: remember { androidx.compose.runtime.mutableStateOf(null) })
    val projects by (projectStore?.projects?.collectAsState()
        ?: remember { androidx.compose.runtime.mutableStateOf(emptyList()) })
    val activeProject = remember(activeProjectId, projects) {
        activeProjectId?.let { id -> projects.find { it.id == id } }
    }

    if (isCodeSpeakMode && navigationCoordinator != null) {
        CodeSpeakThreeSectionToolbar(
            toolbarViewModel = toolbarViewModel,
            remoteControlServer = remoteControlServer,
            navigationCoordinator = navigationCoordinator,
            onOpenSettings = onOpenSettings,
        )
    } else {
        RegularToolbar(
            toolbarState = toolbarState,
            remoteControlServer = remoteControlServer,
            activeProductionURL = activeProject?.productionURL,
            showingChangesPanel = showingChangesPanel,
            onSelectAgent = { toolbarViewModel.selectAgent(it) },
            onPlay = { toolbarViewModel.launchAgent() },
            onStop = { toolbarViewModel.stopAgent() },
            onToggleChangesPanel = onToggleChangesPanel,
            onOpenSettings = onOpenSettings,
            onInstallAgent = onInstallAgent,
        )
    }
}

// ── Regular Mode Toolbar ──────────────────────────────────────────────────────

@Composable
private fun RegularToolbar(
    toolbarState: studio.vibe.shared.viewmodel.ToolbarState,
    remoteControlServer: RemoteControlServer,
    activeProductionURL: String?,
    showingChangesPanel: Boolean,
    onSelectAgent: (AIAgent) -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onToggleChangesPanel: () -> Unit,
    onOpenSettings: () -> Unit,
    onInstallAgent: (AIAgent) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DSLayout.toolbarHeight)
            .background(LocalDSColors.current.surfaceToolbar)
            .padding(horizontal = DSSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))

        AgentPickerButton(
            selectedAgent = toolbarState.selectedAgent,
            availabilityMap = toolbarState.agentAvailability,
            isRunning = toolbarState.isAgentRunning,
            onSelectAgent = onSelectAgent,
            onInstallAgent = onInstallAgent,
        )

        Spacer(Modifier.width(DSSpacing.sm))

        PlayStopButton(
            isRunning = toolbarState.isAgentRunning,
            onPlay = onPlay,
            onStop = onStop,
        )

        Spacer(Modifier.width(DSSpacing.sm))

        // openInBrowser — globe button
        OpenInBrowserButton(productionURL = activeProductionURL)

        Spacer(Modifier.width(DSSpacing.sm))

        // remoteQRButton + remoteIndicator (only when server running)
        RemoteQRButton(server = remoteControlServer)
        RemoteIndicatorButton(server = remoteControlServer)

        // Settings gear
        SettingsButton(onClick = onOpenSettings)

        Spacer(Modifier.width(DSSpacing.sm))

        // changesToggle — View Sidebar icon, highlighted when panel open
        ChangesPanelToggleButton(
            isActive = showingChangesPanel,
            onClick = onToggleChangesPanel,
        )

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

// ── CodeSpeak Three-Section Toolbar ──────────────────────────────────────────

/**
 * Mirrors Swift's `codeSpeakThreeSectionToolbar()`:
 *
 * [Box1: invisible spacer (specsColumnWidth - trafficLightsEnd)]
 * [Box2: breadcrumb + stats badge]
 * [Spacer]
 * [Box3: runBar + remoteQR + remoteIndicator + settings]
 *
 * No changesToggle in CodeSpeak mode (matches Swift behaviour).
 */
@Composable
private fun CodeSpeakThreeSectionToolbar(
    toolbarViewModel: ToolbarViewModel,
    remoteControlServer: RemoteControlServer,
    navigationCoordinator: AppNavigationCoordinator,
    onOpenSettings: () -> Unit,
) {
    val runBarState by navigationCoordinator.runBar.collectAsState()
    val specsColumnWidthPx by navigationCoordinator.specsColumnWidth.collectAsState()
    val density = LocalDensity.current
    val specsColumnWidthDp = with(density) { specsColumnWidthPx.toDp() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DSLayout.toolbarHeight)
            .background(LocalDSColors.current.surfaceToolbar)
            .padding(horizontal = DSSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Box1: invisible spacer to push breadcrumb to start of center column
        val spacerWidth = (specsColumnWidthDp - DSLayout.trafficLightsEnd)
            .coerceAtLeast(0.dp)
        Spacer(Modifier.width(spacerWidth))

        // Box2: breadcrumb + stats badge
        CodeSpeakBreadcrumb(
            runBarState = runBarState,
            onProjectsClick = { /* deactivate project — omitted: no direct API here */ },
        )

        Spacer(Modifier.weight(1f))

        // Box3: runBar + remote + settings
        CodeSpeakRunBarControls(
            runBarState = runBarState,
            onSelectCommand = { cmd ->
                navigationCoordinator.updateRunBar { it.copy(command = cmd) }
            },
            onUpdateTaskName = { name ->
                navigationCoordinator.updateRunBar { it.copy(taskName = name) }
            },
            onUpdateChangeMessage = { msg ->
                navigationCoordinator.updateRunBar { it.copy(changeMessage = msg) }
            },
            onRun = { toolbarViewModel.launchAgent() },
            onStop = { toolbarViewModel.stopAgent() },
            isRunning = runBarState.isRunning,
        )

        RemoteQRButton(server = remoteControlServer)
        RemoteIndicatorButton(server = remoteControlServer)

        SettingsButton(onClick = onOpenSettings)
    }
}

// ── CodeSpeak Breadcrumb ──────────────────────────────────────────────────────

@Composable
private fun CodeSpeakBreadcrumb(
    runBarState: studio.vibe.shared.model.CodeSpeakRunBarState,
    onProjectsClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = DSSpacing.sm),
    ) {
        Text(
            text = "Projects",
            style = DSFont.tabTitle,
            color = LocalDSColors.current.accentPrimary,
            modifier = Modifier.clickable(onClick = onProjectsClick),
        )
        Text(
            text = " › ",
            style = DSFont.tabTitle,
            color = LocalDSColors.current.textMuted,
        )
        if (runBarState.currentSpecName.isNotEmpty()) {
            Text(
                text = runBarState.currentSpecName,
                style = DSFont.tabTitle,
                color = LocalDSColors.current.textPrimary,
            )
            if (runBarState.isEditorDirty) {
                Spacer(Modifier.width(DSSpacing.xs))
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(LocalDSColors.current.gitModified, CircleShape),
                )
            }
        } else {
            Text(
                text = "—",
                style = DSFont.tabTitle,
                color = LocalDSColors.current.textMuted,
            )
        }
    }
}

// ── CodeSpeak Run Bar Controls ────────────────────────────────────────────────

@Composable
private fun CodeSpeakRunBarControls(
    runBarState: studio.vibe.shared.model.CodeSpeakRunBarState,
    onSelectCommand: (CodeSpeakCommand) -> Unit,
    onUpdateTaskName: (String) -> Unit,
    onUpdateChangeMessage: (String) -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit,
    isRunning: Boolean,
) {
    val colors = LocalDSColors.current
    var commandMenuExpanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(DSLayout.toolbarButtonHeight)
            .clip(RoundedCornerShape(DSRadius.md))
            .background(colors.toolbarControlBg)
            .border(1.dp, colors.toolbarControlBorder, RoundedCornerShape(DSRadius.md))
            .padding(horizontal = DSSpacing.xs),
    ) {
        // CodeSpeak agent colour dot
        Box(
            modifier = Modifier
                .size(DSFont.iconBase.value.dp)
                .clip(CircleShape)
                .background(colors.agentCodeSpeak),
        )
        Spacer(Modifier.width(DSSpacing.xxs))

        // Command dropdown
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(enabled = !isRunning) { commandMenuExpanded = true }
                    .padding(horizontal = DSSpacing.xxs),
            ) {
                Text(
                    text = runBarState.command.displayName,
                    style = DSFont.tabTitle,
                    color = if (isRunning) colors.textMuted else colors.textPrimary,
                )
                Spacer(Modifier.width(DSSpacing.xxs))
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Select CodeSpeak command",
                    tint = colors.textMuted,
                    modifier = Modifier.size(DSFont.iconSM.value.dp),
                )
            }
            DropdownMenu(
                expanded = commandMenuExpanded,
                onDismissRequest = { commandMenuExpanded = false },
            ) {
                CodeSpeakCommand.entries.forEach { cmd ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = cmd.displayName,
                                style = DSFont.sidebarItem,
                                color = if (cmd == runBarState.command) colors.accentPrimary else colors.textPrimary,
                            )
                        },
                        onClick = {
                            onSelectCommand(cmd)
                            commandMenuExpanded = false
                        },
                    )
                }
            }
        }

        // Inline input field for task / change commands
        if (runBarState.command.requiresInput) {
            Spacer(Modifier.width(DSSpacing.xs))
            val value = if (runBarState.command == CodeSpeakCommand.TASK)
                runBarState.taskName else runBarState.changeMessage
            val onValueChange: (String) -> Unit =
                if (runBarState.command == CodeSpeakCommand.TASK) onUpdateTaskName
                else onUpdateChangeMessage

            BasicTextField(
                value = value,
                onValueChange = { if (!isRunning) onValueChange(it) },
                enabled = !isRunning,
                textStyle = DSFont.tabTitle.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accentPrimary),
                singleLine = true,
                modifier = Modifier
                    .width(120.dp)
                    .padding(horizontal = DSSpacing.xxs),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = runBarState.command.inputPlaceholder,
                                style = DSFont.tabTitle,
                                color = colors.textMuted,
                            )
                        }
                        inner()
                    }
                },
            )
        }

        Spacer(Modifier.width(DSSpacing.xs))

        // Play / Stop
        Box(
            modifier = Modifier
                .size(DSLayout.toolbarButtonHeight)
                .clickable { if (isRunning) onStop() else onRun() },
            contentAlignment = Alignment.Center,
        ) {
            if (isRunning) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = "Stop",
                    tint = colors.actionStop,
                    modifier = Modifier.size(11.dp),
                )
            } else {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Run CodeSpeak",
                    tint = colors.actionRun,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
    }
}

// ── Open In Browser Button ────────────────────────────────────────────────────

@Composable
private fun OpenInBrowserButton(productionURL: String?) {
    val enabled = productionURL != null
    val colors = LocalDSColors.current
    Box(
        modifier = Modifier
            .size(DSLayout.toolbarIconButtonWidth, DSLayout.toolbarButtonHeight)
            .clickable(enabled = enabled) {
                if (productionURL != null) {
                    runCatching {
                        java.awt.Desktop.getDesktop().browse(URI(productionURL))
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Language,
            contentDescription = if (productionURL != null)
                "Открыть в браузере: $productionURL"
            else
                "Production URL не задан",
            tint = if (enabled) colors.textSecondary else colors.textDisabled,
            modifier = Modifier.size(DSFont.iconBase.value.dp),
        )
    }
}

// ── Remote QR Button ─────────────────────────────────────────────────────────

/**
 * Shows a QR code popup with the remote server URL.
 * Visible only when the server is running.
 */
@Composable
private fun RemoteQRButton(server: RemoteControlServer) {
    val isRunning by server.isRunning.collectAsState()

    val colors = LocalDSColors.current
    var showPopup by remember { mutableStateOf(false) }

    Box {
        Box(
            modifier = Modifier
                .size(DSLayout.toolbarIconButtonWidth, DSLayout.toolbarButtonHeight)
                .clickable { showPopup = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.QrCode2,
                contentDescription = "Показать QR-код удалённого подключения",
                tint = if (isRunning) colors.gitAdded else colors.textMuted,
                modifier = Modifier.size(DSFont.iconBase.value.dp),
            )
        }

        if (showPopup) {
            Popup(
                onDismissRequest = { showPopup = false },
                properties = PopupProperties(focusable = true),
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, DSLayout.toolbarHeight.value.toInt() + 4),
            ) {
                Surface(
                    shape = RoundedCornerShape(DSRadius.md),
                    color = colors.surfaceOverlay,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, colors.borderDefault),
                ) {
                    Column(
                        modifier = Modifier
                            .width(260.dp)
                            .padding(DSSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (!isRunning) {
                            Icon(
                                Icons.Default.QrCode2,
                                contentDescription = null,
                                tint = colors.textMuted,
                                modifier = Modifier.size(48.dp),
                            )
                            Text(
                                text = "Удалённое управление выключено",
                                style = DSFont.bodyMedium,
                                color = colors.textPrimary,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = "Включи в Настройках → Удалённое управление",
                                style = DSFont.sidebarItemSmall,
                                color = colors.textMuted,
                                textAlign = TextAlign.Center,
                            )
                        } else {
                            val pin = server.currentPin
                            val lanIP = remember {
                                runCatching { InetAddress.getLocalHost().hostAddress }.getOrDefault("127.0.0.1")
                            }
                            val lanUrl = "http://$lanIP:${server.port + 1}/?pin=$pin"
                            val ngrokUrl = server.ngrokTunnelURL
                            val primaryUrl = if (ngrokUrl != null) "$ngrokUrl/?pin=$pin" else lanUrl

                            val qrBitmap = remember(primaryUrl) { generateQrBitmap(primaryUrl, 200) }
                            if (qrBitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = qrBitmap,
                                    contentDescription = "QR-код",
                                    modifier = Modifier.size(200.dp),
                                )
                            }

                            SelectionContainer {
                                Text(
                                    text = primaryUrl,
                                    style = DSFont.monoSmall,
                                    color = colors.accentPrimary,
                                )
                            }

                            Text(
                                text = "Отсканируйте камерой телефона",
                                style = DSFont.sidebarItemSmall,
                                color = colors.textMuted,
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(DSRadius.sm))
                                    .background(colors.buttonPrimaryBg)
                                    .clickable {
                                        copyToClipboard(primaryUrl)
                                        showPopup = false
                                    }
                                    .padding(horizontal = DSSpacing.md, vertical = DSSpacing.xs),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = colors.buttonPrimaryText,
                                    modifier = Modifier.size(DSFont.iconBase.value.dp),
                                )
                                Spacer(Modifier.width(DSSpacing.xs))
                                Text(
                                    text = "Скопировать URL",
                                    style = DSFont.smallButtonLabel,
                                    color = colors.buttonPrimaryText,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Remote Indicator Button ───────────────────────────────────────────────────

/**
 * Shows connected device count badge. Visible only when devices > 0.
 * Popup: PIN, device list with disconnect, server status.
 */
@Composable
private fun RemoteIndicatorButton(server: RemoteControlServer) {
    val devices by server.authService.connectedDevices.collectAsState()
    if (devices.isEmpty()) return

    val isRunning by server.isRunning.collectAsState()
    val colors = LocalDSColors.current
    var showPopup by remember { mutableStateOf(false) }
    val pin by server.authService.currentPin.collectAsState()

    Box {
        Row(
            modifier = Modifier
                .height(DSLayout.toolbarButtonHeight)
                .clip(RoundedCornerShape(DSRadius.md))
                .background(colors.toolbarControlBg)
                .border(1.dp, colors.toolbarControlBorder, RoundedCornerShape(DSRadius.md))
                .clickable { showPopup = true }
                .padding(horizontal = DSSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.PhoneIphone,
                contentDescription = "Подключённые устройства: ${devices.size}",
                tint = colors.indicatorRunning,
                modifier = Modifier.size(DSFont.iconLG.value.dp),
            )
            Spacer(Modifier.width(DSSpacing.xxs))
            Text(
                text = devices.size.toString(),
                style = DSFont.statusBadge,
                color = colors.textPrimary,
            )
        }

        if (showPopup) {
            Popup(
                onDismissRequest = { showPopup = false },
                properties = PopupProperties(focusable = true),
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, DSLayout.toolbarHeight.value.toInt() + 4),
            ) {
                Surface(
                    shape = RoundedCornerShape(DSRadius.md),
                    color = colors.surfaceOverlay,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, colors.borderDefault),
                ) {
                    Column(
                        modifier = Modifier
                            .width(280.dp)
                            .padding(DSSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
                    ) {
                        // PIN row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = pin,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = colors.textPrimary,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            Box(
                                modifier = Modifier
                                    .size(DSLayout.toolbarIconButtonWidth, DSLayout.toolbarButtonHeight)
                                    .clickable { server.regeneratePin() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Сгенерировать новый PIN",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(DSFont.iconBase.value.dp),
                                )
                            }
                        }

                        HorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)

                        // Device list
                        devices.forEach { device ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    Icons.Default.PhoneIphone,
                                    contentDescription = null,
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(DSFont.iconLG.value.dp),
                                )
                                Spacer(Modifier.width(DSSpacing.xs))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = device.displayName,
                                        style = DSFont.sidebarItem,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = device.ipAddress,
                                        style = DSFont.sidebarItemSmall,
                                        color = colors.textMuted,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(DSLayout.toolbarIconButtonWidth, DSLayout.toolbarButtonHeight)
                                        .clickable { server.disconnect(device.id) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.Cancel,
                                        contentDescription = "Отключить ${device.displayName}",
                                        tint = colors.gitDeleted,
                                        modifier = Modifier.size(DSFont.iconBase.value.dp),
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)

                        // Server status row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Порт ${server.port}",
                                style = DSFont.monoSmall,
                                color = colors.textSecondary,
                                modifier = Modifier.weight(1f),
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (isRunning) colors.indicatorRunning else colors.indicatorError,
                                        CircleShape,
                                    ),
                            )
                            Spacer(Modifier.width(DSSpacing.xs))
                            Text(
                                text = if (isRunning) "Активен" else "Остановлен",
                                style = DSFont.sidebarItemSmall,
                                color = if (isRunning) colors.indicatorRunning else colors.indicatorError,
                            )
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.width(DSSpacing.sm))
}

// ── Changes Panel Toggle Button ───────────────────────────────────────────────

@Composable
private fun ChangesPanelToggleButton(
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(DSLayout.toolbarIconButtonWidth, DSLayout.toolbarButtonHeight)
            .clip(RoundedCornerShape(DSRadius.md))
            .background(
                if (isActive) LocalDSColors.current.accentPrimary.copy(alpha = 0.18f)
                else Color.Transparent,
            )
            .border(
                width = 1.dp,
                color = if (isActive) LocalDSColors.current.accentPrimary.copy(alpha = 0.5f)
                else Color.Transparent,
                shape = RoundedCornerShape(DSRadius.md),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ViewSidebar,
            contentDescription = if (isActive) "Скрыть панель изменений" else "Показать панель изменений",
            tint = if (isActive) LocalDSColors.current.accentPrimary else LocalDSColors.current.textSecondary,
            modifier = Modifier.size(DSFont.iconBase.value.dp),
        )
    }
}

// ── Agent Picker ──────────────────────────────────────────────────────────────

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
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(DSRadius.md))
                .background(LocalDSColors.current.toolbarControlBg)
                .border(1.dp, LocalDSColors.current.toolbarControlBorder, RoundedCornerShape(DSRadius.md))
                .clickable(enabled = !isRunning) { expanded = true }
                .padding(horizontal = DSSpacing.sm, vertical = DSSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                AgentDropdownItem(
                    agent = agent,
                    status = availabilityMap[agent],
                    isSelected = agent == selectedAgent,
                    onClick = { onSelectAgent(agent); expanded = false },
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
                        { expanded = false; onInstallAgent(agent) }
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
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Spacer(Modifier.width(DSSpacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                when {
                    isSelected && !isNotInstalled -> Text(
                        text = "✓",
                        style = DSFont.smallButtonLabel,
                        color = LocalDSColors.current.accentPrimary,
                    )
                    isNotInstalled -> Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
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

/**
 * Visual brand colour for an agent.
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

/**
 * Generate a QR code bitmap for [url] at [sizePx]×[sizePx].
 * Returns null if the ZXing writer fails for any reason.
 */
private fun generateQrBitmap(
    url: String,
    sizePx: Int,
): androidx.compose.ui.graphics.ImageBitmap? = runCatching {
    val writer = QRCodeWriter()
    val hints = mapOf(EncodeHintType.MARGIN to 1)
    val bitMatrix = writer.encode(url, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val buffered = BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_RGB)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            buffered.setRGB(
                x, y,
                if (bitMatrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt(),
            )
        }
    }
    buffered.toComposeImageBitmap()
}.getOrNull()

private fun copyToClipboard(text: String) {
    runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }
}

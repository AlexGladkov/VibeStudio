@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.shared.model.AIAssistant
import studio.vibe.shared.model.SettingsItem
import studio.vibe.shared.model.SettingsSectionGroup

// ── Public entry point ────────────────────────────────────────────────────────

/**
 * Full settings dialog for VibeStudio Desktop.
 *
 * Displays a two-column layout: a sidebar on the left listing all settings
 * sections and items, and a content pane on the right rendering the selected
 * pane. Matches the Swift VibeStudio settings panel layout and design tokens.
 *
 * @param container The DI container providing access to [GeneralPreferences]
 *   and other platform services.
 * @param onDismiss Called when the dialog should close (X button or window
 *   close request).
 */
@Composable
fun SettingsView(
    container: DesktopServiceContainer,
    onDismiss: () -> Unit,
) {
    val dialogState = rememberDialogState(size = DpSize(860.dp, 680.dp))

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = "Settings",
        resizable = false,
    ) {
        SettingsContent(container = container, onDismiss = onDismiss)
    }
}

// ── Root layout ───────────────────────────────────────────────────────────────

@Composable
private fun SettingsContent(
    container: DesktopServiceContainer,
    onDismiss: () -> Unit,
) {
    // Default selection: first item in GENERAL group
    var selectedItem: SettingsItem by remember {
        mutableStateOf(SettingsSectionGroup.GENERAL.items.first())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DSColor.surfaceBase, RoundedCornerShape(DSRadius.lg))
            .border(1.dp, DSColor.borderDefault, RoundedCornerShape(DSRadius.lg)),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left sidebar
            SettingsSidebar(
                selectedItem = selectedItem,
                onSelectItem = { selectedItem = it },
                modifier = Modifier
                    .width(200.dp)
                    .fillMaxHeight(),
            )

            // 1dp vertical divider
            VerticalDivider(
                color = DSColor.borderDefault,
                modifier = Modifier.fillMaxHeight(),
                thickness = 1.dp,
            )

            // Right content pane
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(DSColor.surfaceBase)
                    .padding(DSSpacing.xl),
            ) {
                SettingsContentPane(
                    item = selectedItem,
                    container = container,
                )
            }
        }

        // Close button — top-right corner
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(DSSpacing.sm),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close settings",
                tint = DSColor.textSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ── Sidebar ───────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSidebar(
    selectedItem: SettingsItem,
    onSelectItem: (SettingsItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(
                color = DSColor.surfaceRaised,
                shape = RoundedCornerShape(
                    topStart = DSRadius.lg,
                    bottomStart = DSRadius.lg,
                    topEnd = 0.dp,
                    bottomEnd = 0.dp,
                ),
            )
            .padding(vertical = DSSpacing.md, horizontal = DSSpacing.xs)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
    ) {
        SettingsSectionGroup.entries.forEach { group ->
            // Section header
            Text(
                text = group.displayName.uppercase(),
                style = DSFont.sidebarSection,
                color = DSColor.textMuted,
                modifier = Modifier.padding(
                    horizontal = DSSpacing.md,
                    vertical = DSSpacing.xxs,
                ),
            )

            Spacer(Modifier.height(DSSpacing.xxs))

            // Items
            group.items.forEach { item ->
                SettingsSidebarRow(
                    item = item,
                    isSelected = selectedItem.id == item.id,
                    onClick = { onSelectItem(item) },
                )
            }

            Spacer(Modifier.height(DSSpacing.md))
        }
    }
}

@Composable
private fun SettingsSidebarRow(
    item: SettingsItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (isSelected) DSColor.accentPrimarySubtle else Color.Transparent
    val textColor = if (isSelected) DSColor.accentPrimary else DSColor.textSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(DSRadius.sm))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = DSSpacing.md, vertical = DSSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icon
        SidebarItemIcon(item = item, tint = textColor)

        Spacer(Modifier.width(DSSpacing.sm))

        // Label
        Text(
            text = item.displayName,
            style = DSFont.sidebarItem,
            color = textColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun SidebarItemIcon(item: SettingsItem, tint: Color) {
    when (item) {
        is SettingsItem.Appearance -> {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
        }
        is SettingsItem.RemoteControl -> {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
        }
        is SettingsItem.LlmAssistant -> {
            // Colored dot representing the agent brand color
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(settingsAgentColor(item.assistant)),
            )
        }
    }
}

// ── Content panes ─────────────────────────────────────────────────────────────

@Composable
private fun SettingsContentPane(
    item: SettingsItem,
    container: DesktopServiceContainer,
) {
    when (item) {
        is SettingsItem.Appearance -> GeneralSettingsPane(prefs = container.generalPreferences)
        is SettingsItem.RemoteControl -> RemoteControlPlaceholder()
        is SettingsItem.LlmAssistant -> LlmSettingsPane(assistant = item.assistant)
    }
}

// ── General / Appearance pane ─────────────────────────────────────────────────

@Composable
private fun GeneralSettingsPane(prefs: studio.vibe.shared.preferences.GeneralPreferences) {
    var confirmTabClose by remember { mutableStateOf(prefs.confirmTabClose) }
    var skipPermissions by remember { mutableStateOf(prefs.claudeSkipPermissions) }
    var fontSize by remember { mutableStateOf(prefs.terminalFontSize.toFloat()) }

    Column(
        verticalArrangement = Arrangement.spacedBy(DSSpacing.xxs),
    ) {
        // Section title
        Text(
            text = "General",
            style = DSFont.settingsTitle,
            color = DSColor.textPrimary,
        )

        Spacer(Modifier.height(DSSpacing.lg))

        // Confirm tab close
        SettingsCheckboxRow(
            label = "Confirm tab close",
            checked = confirmTabClose,
            onCheckedChange = { checked ->
                confirmTabClose = checked
                prefs.confirmTabClose = checked
            },
            accentColor = DSColor.accentPrimary,
        )

        // Skip permissions (Claude)
        SettingsCheckboxRow(
            label = "Skip permissions (Claude)",
            checked = skipPermissions,
            onCheckedChange = { checked ->
                skipPermissions = checked
                prefs.claudeSkipPermissions = checked
            },
            accentColor = DSColor.agentClaude,
        )

        Spacer(Modifier.height(DSSpacing.sm))

        // Terminal font size
        SettingsFontSizeRow(
            fontSize = fontSize,
            onFontSizeChange = { fontSize = it },
            onFontSizeFinished = { prefs.terminalFontSize = fontSize.toInt() },
        )
    }
}

@Composable
private fun SettingsCheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = DSSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(170.dp)) {
            Text(
                text = label,
                style = DSFont.sidebarItem,
                color = DSColor.textPrimary,
            )
        }

        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = accentColor,
                uncheckedColor = DSColor.textMuted,
                checkmarkColor = Color.White,
            ),
        )
    }
}

@Composable
private fun SettingsFontSizeRow(
    fontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    onFontSizeFinished: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DSSpacing.xs),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(170.dp)) {
                Text(
                    text = "Terminal font size",
                    style = DSFont.sidebarItem,
                    color = DSColor.textPrimary,
                )
            }

            Text(
                text = "${fontSize.toInt()} pt",
                style = DSFont.bodySmall,
                color = DSColor.textSecondary,
            )
        }

        Slider(
            value = fontSize,
            onValueChange = onFontSizeChange,
            onValueChangeFinished = onFontSizeFinished,
            valueRange = 9f..24f,
            steps = 14,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = DSColor.accentPrimary,
                activeTrackColor = DSColor.accentPrimary,
                inactiveTrackColor = DSColor.surfaceOverlay,
            ),
        )
    }
}

// ── Remote Control placeholder pane ──────────────────────────────────────────

@Composable
private fun RemoteControlPlaceholder() {
    Column {
        Text(
            text = "Remote Control",
            style = DSFont.settingsTitle,
            color = DSColor.textPrimary,
        )

        Spacer(Modifier.height(DSSpacing.lg))

        Text(
            text = "Remote Control settings",
            style = DSFont.bodyMedium,
            color = DSColor.textSecondary,
        )
    }
}

// ── LLM settings pane ────────────────────────────────────────────────────────

@Composable
private fun LlmSettingsPane(assistant: AIAssistant) {
    Column {
        // Title
        Text(
            text = assistant.displayName,
            style = DSFont.settingsTitle,
            color = DSColor.textPrimary,
        )

        Spacer(Modifier.height(DSSpacing.lg))

        // Short description
        Text(
            text = assistant.shortDescription,
            style = DSFont.bodyMedium,
            color = DSColor.textSecondary,
        )

        Spacer(Modifier.height(DSSpacing.xl))

        // API key section
        val envVar = assistant.apiKeyEnvironmentVariable
        if (envVar != null) {
            LlmApiKeySection(assistant = assistant, envVar = envVar)
        } else {
            // Claude / OpenCode — no API key needed
            LlmNoKeySection(assistant = assistant)
        }

        // Install hint
        Spacer(Modifier.height(DSSpacing.xl))
        LlmInstallSection(assistant = assistant)
    }
}

@Composable
private fun LlmApiKeySection(assistant: AIAssistant, envVar: String) {
    Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
        Text(
            text = "API key configuration for ${assistant.displayName}",
            style = DSFont.sidebarSection,
            color = DSColor.textPrimary,
        )

        Text(
            text = "Configure in environment variables",
            style = DSFont.bodySmall,
            color = DSColor.textSecondary,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(DSRadius.md))
                .background(DSColor.surfaceOverlay)
                .border(1.dp, DSColor.borderSubtle, RoundedCornerShape(DSRadius.md))
                .padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "export $envVar=your-key-here",
                style = DSFont.monoPath,
                color = DSColor.accentPrimary,
            )
        }

        assistant.setupInstructions?.let { instructions ->
            Spacer(Modifier.height(DSSpacing.xs))
            Text(
                text = instructions,
                style = DSFont.bodySmall,
                color = DSColor.textMuted,
            )
        }
    }
}

@Composable
private fun LlmNoKeySection(assistant: AIAssistant) {
    Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
        Text(
            text = "Authentication",
            style = DSFont.sidebarSection,
            color = DSColor.textPrimary,
        )

        val instructions = assistant.setupInstructions
        if (instructions != null) {
            Text(
                text = instructions,
                style = DSFont.bodySmall,
                color = DSColor.textSecondary,
            )
        } else {
            Text(
                text = "No API key required — uses environment-based authentication.",
                style = DSFont.bodySmall,
                color = DSColor.textSecondary,
            )
        }
    }
}

@Composable
private fun LlmInstallSection(assistant: AIAssistant) {
    Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
        Text(
            text = "Installation",
            style = DSFont.sidebarSection,
            color = DSColor.textPrimary,
        )

        assistant.prerequisite?.let { prereq ->
            Text(
                text = "Requires: $prereq",
                style = DSFont.bodySmall,
                color = DSColor.textMuted,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(DSRadius.md))
                .background(DSColor.surfaceOverlay)
                .border(1.dp, DSColor.borderSubtle, RoundedCornerShape(DSRadius.md))
                .padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
        ) {
            Text(
                text = assistant.installHint,
                style = DSFont.monoPath,
                color = DSColor.textSecondary,
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Maps an [AIAssistant] to its brand accent color using the design system tokens.
 *
 * This mirrors the private `agentColor()` function in [ToolbarView] — kept
 * private here to avoid coupling the two files.
 */
private fun settingsAgentColor(assistant: AIAssistant): Color = when (assistant) {
    AIAssistant.CLAUDE -> DSColor.agentClaude
    AIAssistant.OPENCODE -> DSColor.agentOpenCode
    AIAssistant.CODEX -> DSColor.agentCodex
    AIAssistant.GEMINI -> DSColor.agentGemini
    AIAssistant.QWEN_CODE -> DSColor.agentQwen
    AIAssistant.CODE_SPEAK -> DSColor.agentCodeSpeak
}


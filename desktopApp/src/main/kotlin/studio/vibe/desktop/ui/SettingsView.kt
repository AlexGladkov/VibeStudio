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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import studio.vibe.desktop.ui.settings.GeneralSettingsPane
import studio.vibe.desktop.ui.settings.LlmSettingsPane
import studio.vibe.desktop.ui.settings.RemoteControlSettingsPane
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSColors
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.shared.model.AIAssistant
import studio.vibe.shared.model.SettingsItem
import studio.vibe.shared.model.SettingsSectionGroup
import studio.vibe.shared.preferences.AppTheme

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
    onThemeChange: (AppTheme) -> Unit = {},
) {
    val dialogState = rememberDialogState(size = DpSize(860.dp, 680.dp))

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = "Settings",
        resizable = false,
    ) {
        SettingsContent(
            container = container,
            onDismiss = onDismiss,
            onThemeChange = onThemeChange,
        )
    }
}

// ── Root layout ───────────────────────────────────────────────────────────────

@Composable
private fun SettingsContent(
    container: DesktopServiceContainer,
    onDismiss: () -> Unit,
    onThemeChange: (AppTheme) -> Unit,
) {
    val colors = LocalDSColors.current
    // Default selection: first item in GENERAL group
    var selectedItem: SettingsItem by remember {
        mutableStateOf(SettingsSectionGroup.GENERAL.items.first())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceBase, RoundedCornerShape(DSRadius.lg))
            .border(1.dp, colors.borderDefault, RoundedCornerShape(DSRadius.lg)),
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
                color = colors.borderDefault,
                modifier = Modifier.fillMaxHeight(),
                thickness = 1.dp,
            )

            // Right content pane
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(colors.surfaceBase),
            ) {
                SettingsContentPane(
                    item = selectedItem,
                    container = container,
                    onThemeChange = onThemeChange,
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
                tint = colors.textSecondary,
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
    val colors = LocalDSColors.current
    Column(
        modifier = modifier
            .background(
                color = colors.surfaceRaised,
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
                color = colors.textMuted,
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
    val colors = LocalDSColors.current
    val bgColor = if (isSelected) colors.accentPrimarySubtle else Color.Transparent
    val textColor = if (isSelected) colors.accentPrimary else colors.textSecondary

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
    val colors = LocalDSColors.current
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
                    .background(settingsAgentColor(item.assistant, colors)),
            )
        }
    }
}

// ── Content pane router ───────────────────────────────────────────────────────

@Composable
private fun SettingsContentPane(
    item: SettingsItem,
    container: DesktopServiceContainer,
    onThemeChange: (AppTheme) -> Unit,
) {
    when (item) {
        is SettingsItem.Appearance -> GeneralSettingsPane(
            container = container,
            onThemeChange = onThemeChange,
        )
        is SettingsItem.RemoteControl -> RemoteControlSettingsPane(container = container)
        is SettingsItem.LlmAssistant -> LlmSettingsPane(
            assistant = item.assistant,
            container = container,
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Maps an [AIAssistant] to its brand accent color using the design system tokens.
 *
 * This mirrors the private `agentColor()` function in [ToolbarView] — kept
 * private here to avoid coupling the two files.
 */
private fun settingsAgentColor(assistant: AIAssistant, colors: DSColors): Color = when (assistant) {
    AIAssistant.CLAUDE -> colors.agentClaude
    AIAssistant.OPENCODE -> colors.agentOpenCode
    AIAssistant.CODEX -> colors.agentCodex
    AIAssistant.GEMINI -> colors.agentGemini
    AIAssistant.QWEN_CODE -> colors.agentQwen
    AIAssistant.CODE_SPEAK -> colors.agentCodeSpeak
}

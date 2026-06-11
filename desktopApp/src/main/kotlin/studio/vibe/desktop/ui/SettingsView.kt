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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import studio.vibe.desktop.remote.RemoteControlServer
import studio.vibe.shared.feature.codespeak.data.CodeSpeakPreferences
import studio.vibe.shared.feature.settings.data.GeneralPreferences
import studio.vibe.shared.feature.settings.data.RemoteControlPreferences
import studio.vibe.desktop.ui.settings.GeneralSettingsPane
import studio.vibe.desktop.ui.settings.LlmSettingsPane
import studio.vibe.desktop.ui.settings.RemoteControlSettingsPane
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSColors
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.desktop.ui.theme.VibeStudioTheme
import studio.vibe.shared.core.common.AIAgent
import studio.vibe.shared.core.common.AIAgentRegistry
import studio.vibe.shared.feature.settings.domain.model.SettingsItem
import studio.vibe.shared.feature.settings.domain.model.SettingsSectionGroup
import studio.vibe.shared.feature.settings.data.AppTheme

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
    generalPreferences: GeneralPreferences,
    codeSpeakPreferences: CodeSpeakPreferences,
    remoteControlPreferences: RemoteControlPreferences,
    remoteControlServer: RemoteControlServer,
    agentRegistry: AIAgentRegistry,
    onDismiss: () -> Unit,
) {
    val dialogState = rememberDialogState(size = DpSize(860.dp, 680.dp))
    val theme by generalPreferences.themeFlow.collectAsState()
    val isDark = when (theme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.DARK -> true
        AppTheme.LIGHT -> false
    }

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = "Settings",
        resizable = false,
    ) {
        VibeStudioTheme(isDark = isDark) {
            SettingsContent(
                generalPreferences = generalPreferences,
                codeSpeakPreferences = codeSpeakPreferences,
                remoteControlPreferences = remoteControlPreferences,
                remoteControlServer = remoteControlServer,
                agentRegistry = agentRegistry,
                onDismiss = onDismiss,
            )
        }
    }
}

// ── Root layout ───────────────────────────────────────────────────────────────

@Composable
private fun SettingsContent(
    generalPreferences: GeneralPreferences,
    codeSpeakPreferences: CodeSpeakPreferences,
    remoteControlPreferences: RemoteControlPreferences,
    remoteControlServer: RemoteControlServer,
    agentRegistry: AIAgentRegistry,
    onDismiss: () -> Unit,
) {
    val colors = LocalDSColors.current
    // Collect agents as state so Compose recomposes when plugin agents are registered/unregistered
    val registryAgents by agentRegistry.agents.collectAsState()
    // Default selection: first item in GENERAL group
    var selectedItem: SettingsItem by remember {
        mutableStateOf(SettingsItem.Appearance)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceBase, RoundedCornerShape(DSRadius.lg))
            .border(1.dp, colors.borderDefault, RoundedCornerShape(DSRadius.lg)),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left sidebar — registryAgents collected above drives recomposition
            SettingsSidebar(
                selectedItem = selectedItem,
                onSelectItem = { selectedItem = it },
                registryAgents = registryAgents,
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
                    generalPreferences = generalPreferences,
                    codeSpeakPreferences = codeSpeakPreferences,
                    remoteControlPreferences = remoteControlPreferences,
                    remoteControlServer = remoteControlServer,
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
                contentDescription = "Закрыть",
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
    registryAgents: List<AIAgent>,
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

            // Items — LLM section built from registry snapshot so plugin agents appear automatically
            val items = when (group) {
                SettingsSectionGroup.GENERAL -> listOf(SettingsItem.Appearance, SettingsItem.RemoteControl)
                SettingsSectionGroup.LLM -> registryAgents.map { SettingsItem.LlmAgent(it) }
            }
            items.forEach { item ->
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
        is SettingsItem.LlmAgent -> {
            // Colored dot representing the agent brand color (registry path)
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(settingsAgentColorById(item.agent.id, colors)),
            )
        }
    }
}

// ── Content pane router ───────────────────────────────────────────────────────

@Composable
private fun SettingsContentPane(
    item: SettingsItem,
    generalPreferences: GeneralPreferences,
    codeSpeakPreferences: CodeSpeakPreferences,
    remoteControlPreferences: RemoteControlPreferences,
    remoteControlServer: RemoteControlServer,
) {
    when (item) {
        is SettingsItem.Appearance -> GeneralSettingsPane(
            preferences = generalPreferences,
        )
        is SettingsItem.RemoteControl -> RemoteControlSettingsPane(
            preferences = remoteControlPreferences,
            server = remoteControlServer,
        )
        is SettingsItem.LlmAgent -> LlmSettingsPane(
            agent = item.agent,
            generalPreferences = generalPreferences,
            codeSpeakPreferences = codeSpeakPreferences,
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun settingsAgentColorById(agentId: String, colors: DSColors): Color = when (agentId) {
    "claude"    -> colors.agentClaude
    "opencode"  -> colors.agentOpenCode
    "codex"     -> colors.agentCodex
    "gemini"    -> colors.agentGemini
    "qwenCode"  -> colors.agentQwen
    "codeSpeak" -> colors.agentCodeSpeak
    else -> colors.textSecondary
}

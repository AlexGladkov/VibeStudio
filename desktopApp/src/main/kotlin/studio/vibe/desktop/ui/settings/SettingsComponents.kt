package studio.vibe.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing

// -- SettingsSectionHeader -----------------------------------------------------

/**
 * Section header for settings lists with an optional add (+) button.
 *
 * Mirrors Swift SettingsSectionHeader.swift.
 */
@Composable
fun SettingsSectionHeader(
    title: String,
    showAddButton: Boolean = false,
    onAdd: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = DSFont.buttonLabel,
            color = DSColor.textSecondary,
        )

        Spacer(Modifier.weight(1f))

        if (showAddButton && onAdd != null) {
            IconButton(
                onClick = onAdd,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add $title",
                    tint = DSColor.textSecondary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

// -- SettingsItemRow -----------------------------------------------------------

/**
 * A row displaying a named item with an optional subtitle, edit button, and delete button.
 *
 * Used across all settings panes (Claude, Codex, OpenCode, Qwen) to display
 * agents, commands, memories, plugins, and skills in a consistent style.
 * The row does NOT include dividers — those are added by the parent list.
 *
 * Mirrors Swift SettingsItemRow.swift.
 */
@Composable
fun SettingsItemRow(
    name: String,
    subtitle: String? = null,
    showDelete: Boolean = true,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(DSSpacing.xs),
        ) {
            Text(
                text = name,
                style = DSFont.buttonLabel,
                color = DSColor.textPrimary,
                maxLines = 1,
            )

            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    style = DSFont.sidebarItemSmall,
                    color = DSColor.textMuted,
                    maxLines = 1,
                )
            }
        }

        if (onEdit != null) {
            Spacer(Modifier.width(DSSpacing.sm))
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit $name",
                    tint = DSColor.textSecondary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        if (showDelete && onDelete != null) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete $name",
                    tint = DSColor.gitDeleted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

// -- SettingsCard --------------------------------------------------------------

/**
 * A card container used throughout settings panes.
 *
 * Mirrors the Swift `.settingsCard()` ViewModifier.
 */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DSRadius.md))
            .background(DSColor.surfaceOverlay)
            .border(1.dp, DSColor.borderSubtle, RoundedCornerShape(DSRadius.md)),
    ) {
        content()
    }
}

// -- SettingsEmptyState --------------------------------------------------------

/**
 * Empty state shown inside settings pane lists when there are no items.
 */
@Composable
fun SettingsEmptyState(text: String) {
    SettingsCard {
        Text(
            text = text,
            style = DSFont.bodySmall,
            color = DSColor.textMuted,
            modifier = Modifier.padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
        )
    }
}

// -- SettingsDivider -----------------------------------------------------------

/**
 * A divider styled for settings pane lists.
 */
@Composable
fun SettingsListDivider() {
    HorizontalDivider(
        color = DSColor.borderSubtle,
        thickness = 1.dp,
        modifier = Modifier.padding(horizontal = DSSpacing.md),
    )
}

// -- SettingsToggleRow ---------------------------------------------------------

/**
 * A full-width toggle row with title and description, styled for settings cards.
 *
 * Used in CodeSpeakSettingsPane preference sections.
 */
@Composable
fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(DSSpacing.xxs),
        ) {
            Text(
                text = title,
                style = DSFont.buttonLabel,
                color = DSColor.textPrimary,
            )
            Text(
                text = description,
                style = DSFont.sidebarItemSmall,
                color = DSColor.textSecondary,
            )
        }

        Spacer(Modifier.width(DSSpacing.md))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = DSColor.accentPrimary,
                uncheckedThumbColor = DSColor.textMuted,
                uncheckedTrackColor = DSColor.surfaceOverlay,
            ),
        )
    }
}

// -- SettingsLabelRow ----------------------------------------------------------

/**
 * A row with a fixed-width label on the left and trailing content on the right.
 *
 * Mirrors the HStack + `.frame(width: DSLayout.settingsLabelWidth)` pattern
 * used throughout Swift settings panes.
 */
@Composable
fun SettingsLabelRow(
    label: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = DSFont.sidebarItem,
            color = DSColor.textPrimary,
            modifier = Modifier.width(170.dp),
        )
        trailing()
    }
}

// -- SettingsMonoBlock ---------------------------------------------------------

/**
 * A monospaced code block inside a SettingsCard, used for export/env-var snippets.
 */
@Composable
fun SettingsMonoBlock(
    text: String,
    modifier: Modifier = Modifier,
) {
    SettingsCard(modifier = modifier) {
        Text(
            text = text,
            style = DSFont.monoPath,
            color = DSColor.accentPrimary,
            modifier = Modifier.padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
        )
    }
}

// -- SettingsTitleDivider ------------------------------------------------------

/**
 * Title + horizontal divider pair used at the top of every settings pane.
 */
@Composable
fun SettingsPaneTitle(title: String) {
    Text(
        text = title,
        style = DSFont.settingsTitle,
        color = DSColor.textPrimary,
    )
    Spacer(Modifier.height(DSSpacing.xs))
    HorizontalDivider(color = DSColor.borderDefault, thickness = 1.dp)
}

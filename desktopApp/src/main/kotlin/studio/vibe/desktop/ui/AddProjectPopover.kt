@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.Project
import studio.vibe.shared.viewmodel.AddProjectViewModel
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.Clock

// ── AddProjectPopover ─────────────────────────────────────────────────────────

/**
 * Popup/dropdown for the "+" button in the sidebar icon strip.
 *
 * Mirrors AddProjectPopover.swift (225 LOC).
 *
 * Shows:
 * - Recent projects list (folder icon, name, path, relative date)
 * - Empty state when no recent projects
 * - "Open Folder..." action button
 * - Optional "Create New..." action button
 *
 * Width: 280–360dp.
 *
 * @param container DI container providing [studio.vibe.shared.contract.ProjectManaging].
 * @param onOpenFolder Called when user taps "Open Folder...".
 * @param onCreateNew Called when user taps "Create New..."; pass null to hide the button.
 * @param onDismiss Called after a project is opened or actions are triggered.
 */
@Composable
public fun AddProjectPopover(
    projectStore: ProjectManaging,
    onOpenFolder: () -> Unit,
    onCreateNew: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val vm = remember {
        AddProjectViewModel(
            projectManaging = projectStore,
            scope = coroutineScope,
        )
    }
    val state by vm.state.collectAsState()
    val recentProjects = state.recentProjects

    Column(
        modifier = Modifier
            .widthIn(min = 280.dp, max = 360.dp)
            .background(LocalDSColors.current.surfaceOverlay)
            .padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
    ) {
        if (recentProjects.isEmpty()) {
            // ── Empty state ───────────────────────────────────────────────
            PopoverEmptyState()
            HorizontalDivider(
                color = LocalDSColors.current.borderSubtle,
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = DSSpacing.xs),
            )
        } else {
            // ── Recents list ──────────────────────────────────────────────
            Text(
                text = "RECENT",
                style = DSFont.sidebarSection,
                color = LocalDSColors.current.textSecondary,
                modifier = Modifier.padding(bottom = DSSpacing.xs),
            )
            recentProjects.forEach { project ->
                RecentRow(
                    project = project,
                    onTap = {
                        vm.openRecentProject(project.path)
                        onDismiss()
                    },
                )
            }
            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage!!,
                    style = DSFont.sidebarItemSmall,
                    color = LocalDSColors.current.gitDeleted,
                    maxLines = 2,
                    modifier = Modifier.padding(top = DSSpacing.xs),
                )
            }
            HorizontalDivider(
                color = LocalDSColors.current.borderSubtle,
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = DSSpacing.xs),
            )
        }

        // ── Action buttons ────────────────────────────────────────────────
        ActionRow(
            title = "Open Folder...",
            icon = Icons.Filled.FolderOpen,
        ) {
            onOpenFolder()
            onDismiss()
        }

        if (onCreateNew != null) {
            ActionRow(
                title = "Create New...",
                icon = Icons.Filled.Add,
            ) {
                onCreateNew()
                onDismiss()
            }
        }
    }
}

// ── EmptyState ────────────────────────────────────────────────────────────────

@Composable
private fun PopoverEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = DSSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
    ) {
        Icon(
            imageVector = Icons.Filled.Folder,
            contentDescription = null,
            tint = LocalDSColors.current.textMuted,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = "No recent projects",
            style = DSFont.sidebarItem,
            color = LocalDSColors.current.textSecondary,
        )
        Text(
            text = "Open a folder or create a new project",
            style = DSFont.sidebarItemSmall,
            color = LocalDSColors.current.textMuted,
        )
    }
}

// ── RecentRow ─────────────────────────────────────────────────────────────────

/**
 * Single row displaying a recent project: folder icon, name, tilde-abbreviated path,
 * and a relative date label.
 */
@Composable
private fun RecentRow(
    project: Project,
    onTap: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DSRadius.sm))
            .background(if (isHovered) LocalDSColors.current.hoverOverlay else LocalDSColors.current.surfaceOverlay.copy(alpha = 0f))
            .hoverable(interactionSource)
            .clickable(onClick = onTap)
            .padding(horizontal = DSSpacing.sm, vertical = DSSpacing.xs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = LocalDSColors.current.gitModified,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(DSSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    style = DSFont.sidebarItem,
                    color = LocalDSColors.current.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = project.path.path.replace(System.getProperty("user.home") ?: "", "~"),
                        style = DSFont.sidebarItemSmall,
                        color = LocalDSColors.current.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(DSSpacing.xs))
                    Text(
                        text = formatRelativeDate(project.lastOpened),
                        style = DSFont.sidebarItemSmall,
                        color = LocalDSColors.current.textSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ── ActionRow ─────────────────────────────────────────────────────────────────

/**
 * Styled action button row (e.g. "Open Folder...", "Create New...").
 */
@Composable
private fun ActionRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    action: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DSRadius.sm))
            .background(if (isHovered) LocalDSColors.current.hoverOverlay else LocalDSColors.current.surfaceOverlay.copy(alpha = 0f))
            .hoverable(interactionSource)
            .clickable(onClick = action)
            .padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = LocalDSColors.current.textPrimary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(DSSpacing.sm))
            Text(
                text = title,
                style = DSFont.sidebarItem,
                color = LocalDSColors.current.textPrimary,
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Formats an [Instant] as a human-readable relative date string.
 *
 * Mirrors Swift's `.relative(presentation: .named)` format style.
 */
@OptIn(ExperimentalTime::class)
private fun formatRelativeDate(instant: Instant): String {
    val now = Clock.System.now()
    val delta = now - instant
    val totalSeconds = delta.inWholeSeconds
    return when {
        totalSeconds < 60 -> "только что"
        totalSeconds < 3600 -> "${totalSeconds / 60} мин. назад"
        totalSeconds < 86400 -> "${totalSeconds / 3600} ч. назад"
        totalSeconds < 86400 * 7 -> "${totalSeconds / 86400} д. назад"
        totalSeconds < 86400 * 30 -> "${totalSeconds / (86400 * 7)} нед. назад"
        totalSeconds < 86400 * 365 -> "${totalSeconds / (86400 * 30)} мес. назад"
        else -> "${totalSeconds / (86400 * 365)} г. назад"
    }
}

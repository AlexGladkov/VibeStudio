@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSLayout
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.shared.model.DirectoryEntry
import studio.vibe.shared.model.FileEntry
import studio.vibe.shared.model.FileTreeNode
import studio.vibe.shared.model.GitFileStatus

// ── Public API ────────────────────────────────────────────────────────────────

/**
 * Displays a hierarchical project file tree in a sidebar column.
 *
 * The component renders a [LazyColumn] at the root level and falls back to
 * nested [Column]s for expanded sub-trees so that [LazyColumn] nesting
 * constraints are respected.
 *
 * @param nodes        Root-level nodes returned by [FileTreeBuilder.buildTree].
 * @param expandedDirs Set of path strings identifying expanded directories.
 * @param onToggleDir  Callback invoked with the directory path when a directory
 *                     row is clicked to toggle its expanded state.
 * @param modifier     Optional [Modifier] for the outer [LazyColumn].
 */
@Composable
public fun FileTreeView(
    nodes: List<FileTreeNode>,
    expandedDirs: Set<String>,
    onToggleDir: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(nodes, key = { it.id }) { node ->
            FileTreeNodeItem(
                node = node,
                depth = 0,
                expandedDirs = expandedDirs,
                onToggleDir = onToggleDir,
            )
        }
    }
}

// ── Node dispatcher ───────────────────────────────────────────────────────────

@Composable
private fun FileTreeNodeItem(
    node: FileTreeNode,
    depth: Int,
    expandedDirs: Set<String>,
    onToggleDir: (String) -> Unit,
) {
    when (node) {
        is FileTreeNode.Directory -> DirectoryRow(
            entry = node.entry,
            depth = depth,
            expandedDirs = expandedDirs,
            onToggleDir = onToggleDir,
        )
        is FileTreeNode.File -> FileRow(
            entry = node.entry,
            depth = depth,
        )
    }
}

// ── Directory row ─────────────────────────────────────────────────────────────

@Composable
private fun DirectoryRow(
    entry: DirectoryEntry,
    depth: Int,
    expandedDirs: Set<String>,
    onToggleDir: (String) -> Unit,
) {
    val isExpanded = entry.path.path in expandedDirs
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "chevron-rotation-${entry.path.path}",
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val leadingPadding = (depth * DSLayout.treeIndent.value + DSLayout.treeBaseIndent.value).dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DSLayout.treeRowHeight)
            .hoverable(interactionSource)
            .clickable { onToggleDir(entry.path.path) }
            .background(if (isHovered) DSColor.hoverOverlay else Color.Transparent)
            .padding(start = leadingPadding, end = DSLayout.sidebarHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Chevron — rotates 90° when expanded
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = DSColor.textMuted,
            modifier = Modifier
                .size(DSFont.iconSM.value.dp)
                .rotate(chevronRotation),
        )

        Spacer(Modifier.width(DSSpacing.xs))

        // Folder icon
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = DSColor.gitModified,
            modifier = Modifier.size(DSFont.iconLG.value.dp),
        )

        Spacer(Modifier.width(DSSpacing.xs))

        // Directory name
        Text(
            text = entry.path.name,
            style = DSFont.sidebarItem,
            color = DSColor.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }

    // Render children in a nested Column when expanded.
    // LazyColumn cannot contain another LazyColumn, so sub-trees use Column.
    if (isExpanded && entry.children.isNotEmpty()) {
        Column {
            for (child in entry.children) {
                FileTreeNodeItem(
                    node = child,
                    depth = depth + 1,
                    expandedDirs = expandedDirs,
                    onToggleDir = onToggleDir,
                )
            }
        }
    }
}

// ── File row ──────────────────────────────────────────────────────────────────

@Composable
private fun FileRow(
    entry: FileEntry,
    depth: Int,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val leadingPadding = (depth * DSLayout.treeIndent.value + DSLayout.treeBaseIndent.value).dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DSLayout.treeRowHeight)
            .hoverable(interactionSource)
            .background(if (isHovered) DSColor.hoverOverlay else Color.Transparent)
            .padding(start = leadingPadding, end = DSLayout.sidebarHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Spacer replaces the chevron to keep text aligned with directory rows
        Spacer(Modifier.width(DSFont.iconSM.value.dp))

        Spacer(Modifier.width(DSSpacing.xs))

        // File icon resolved from extension
        val extension = entry.path.name.substringAfterLast('.', "")
        val (fileIcon, iconTint) = fileIconForExtension(extension)

        Icon(
            imageVector = fileIcon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(DSFont.iconLG.value.dp),
        )

        Spacer(Modifier.width(DSSpacing.xs))

        // File name
        Text(
            text = entry.path.name,
            style = DSFont.sidebarItem,
            color = DSColor.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // Git status letter — right-aligned, shown only when status is present.
        // Captured into a local val to allow smart-cast across module boundary.
        val gitStatus = entry.gitStatus
        if (gitStatus != null) {
            Spacer(Modifier.width(DSSpacing.xs))
            Text(
                text = gitStatus.code,
                style = DSFont.gitStatus,
                color = statusColor(gitStatus),
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Maps a file extension to an icon/tint pair following the design spec. */
private fun fileIconForExtension(extension: String): Pair<ImageVector, Color> = when (extension.lowercase()) {
    "kt", "kts" -> Icons.Default.Code to DSColor.accentPrimary
    "swift" -> Icons.Default.Code to Color(0xFFF05138)
    "json", "yaml", "yml", "toml" -> Icons.Default.Settings to DSColor.textSecondary
    "md", "txt" -> Icons.Default.Description to DSColor.textSecondary
    "xml", "html" -> Icons.Default.Code to DSColor.gitModified
    "gradle" -> Icons.Default.Build to DSColor.textSecondary
    "png", "jpg", "jpeg", "svg" -> Icons.Default.Image to DSColor.textSecondary
    else -> Icons.AutoMirrored.Filled.InsertDriveFile to DSColor.textSecondary
}

/** Returns the color associated with a [GitFileStatus] value. */
private fun statusColor(status: GitFileStatus): Color = when (status) {
    GitFileStatus.MODIFIED -> DSColor.gitModified
    GitFileStatus.ADDED -> DSColor.gitAdded
    GitFileStatus.DELETED -> DSColor.gitDeleted
    GitFileStatus.RENAMED -> DSColor.gitRenamed
    GitFileStatus.COPIED -> DSColor.gitAdded
    GitFileStatus.UNTRACKED -> DSColor.gitUntracked
}

@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui.sidebar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import studio.vibe.shared.contract.GitServicing
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.service.filetree.FileTreeBuilder
import studio.vibe.desktop.ui.GitRemoteSetupSheet
import studio.vibe.desktop.ui.ProjectSettingsSheet
import studio.vibe.desktop.ui.theme.DSColors
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSLayout
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.shared.model.DirectoryEntry
import studio.vibe.shared.model.FileEntry
import studio.vibe.shared.model.FileTreeNode
import studio.vibe.shared.model.GitFileStatus
import studio.vibe.shared.model.Project

private sealed class FileTreeDialog {
    data class ProjectSettings(val project: Project) : FileTreeDialog()
    data class GitRemoteSetup(val project: Project) : FileTreeDialog()
}

@Composable
internal fun MultiProjectFileTree(
    projectStore: ProjectManaging,
    fileTreeBuilder: FileTreeBuilder,
    gitService: GitServicing,
    coroutineScope: CoroutineScope,
    projects: List<Project>,
    activeProjectId: Uuid?,
) {
    var expandedProjects by remember { mutableStateOf(setOf<Uuid>()) }
    var expandedDirs by remember { mutableStateOf(setOf<String>()) }
    var fileTrees by remember { mutableStateOf(mapOf<Uuid, List<FileTreeNode>>()) }
    var activeDialog by remember { mutableStateOf<FileTreeDialog?>(null) }

    LaunchedEffect(activeProjectId) {
        if (activeProjectId != null && activeProjectId !in expandedProjects) {
            expandedProjects = expandedProjects + activeProjectId
        }
    }

    for (project in projects) {
        if (project.id in expandedProjects && project.id !in fileTrees) {
            LaunchedEffect(project.id) {
                val tree = fileTreeBuilder.buildTree(root = project.path, maxDepth = 5)
                fileTrees = fileTrees + (project.id to tree)
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = DSSpacing.sm)) {
        for (project in projects) {
            val isActive = project.id == activeProjectId
            val isExpanded = project.id in expandedProjects

            item(key = "project-${project.id}") {
                ProjectHeaderRow(
                    project = project,
                    isActive = isActive,
                    isExpanded = isExpanded,
                    onClick = { projectStore.setActiveProjectId(project.id) },
                    onToggleExpand = {
                        expandedProjects = if (isExpanded) {
                            expandedProjects - project.id
                        } else {
                            expandedProjects + project.id
                        }
                    },
                    onOpenSettings = { activeDialog = FileTreeDialog.ProjectSettings(project) },
                    onOpenGitRemote = { activeDialog = FileTreeDialog.GitRemoteSetup(project) },
                    onRevealInFinder = { revealInFinder(project.path.path) },
                    onRemove = { coroutineScope.launch { projectStore.removeProject(project.id) } },
                )
            }

            if (isExpanded) {
                val nodes = fileTrees[project.id] ?: emptyList()
                items(
                    flattenTree(nodes, expandedDirs, depth = 1),
                    key = { it.id },
                ) { flatNode ->
                    when (val node = flatNode.node) {
                        is FileTreeNode.Directory -> DirectoryRow(
                            entry = node.entry,
                            depth = flatNode.depth,
                            isExpanded = node.entry.path.path in expandedDirs,
                            onToggle = {
                                expandedDirs = if (node.entry.path.path in expandedDirs) {
                                    expandedDirs - node.entry.path.path
                                } else {
                                    expandedDirs + node.entry.path.path
                                }
                            },
                        )
                        is FileTreeNode.File -> FileRow(
                            entry = node.entry,
                            depth = flatNode.depth,
                        )
                    }
                }
            }
        }
    }

    when (val dialog = activeDialog) {
        is FileTreeDialog.ProjectSettings -> ProjectSettingsSheet(
            projectStore = projectStore,
            projectId = dialog.project.id,
            projectName = dialog.project.name,
            projectPath = dialog.project.path.path,
            onDismiss = { activeDialog = null },
        )
        is FileTreeDialog.GitRemoteSetup -> GitRemoteSetupSheet(
            gitService = gitService,
            coroutineScope = coroutineScope,
            projectId = dialog.project.id,
            projectPath = dialog.project.path,
            projectName = dialog.project.name,
            onDismiss = { activeDialog = null },
        )
        null -> Unit
    }
}

internal data class FlatTreeItem(val id: String, val node: FileTreeNode, val depth: Int)

internal fun flattenTree(
    nodes: List<FileTreeNode>,
    expandedDirs: Set<String>,
    depth: Int,
): List<FlatTreeItem> {
    val result = mutableListOf<FlatTreeItem>()
    for (node in nodes) {
        result.add(FlatTreeItem(id = node.id, node = node, depth = depth))
        if (node is FileTreeNode.Directory && node.entry.path.path in expandedDirs) {
            result.addAll(flattenTree(node.entry.children, expandedDirs, depth + 1))
        }
    }
    return result
}

@Composable
private fun ProjectHeaderRow(
    project: Project,
    isActive: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onToggleExpand: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenGitRemote: () -> Unit = {},
    onRevealInFinder: () -> Unit = {},
    onRemove: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(150),
    )

    val menuItems = listOf(
        ContextMenuItem("Project Settings") { onOpenSettings() },
        ContextMenuItem("Configure Git Remote") { onOpenGitRemote() },
        ContextMenuItem("Reveal in Finder") { onRevealInFinder() },
        ContextMenuItem("Remove") { onRemove() },
    )

    ContextMenuArea(items = { menuItems }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DSLayout.treeRowHeight)
                .hoverable(interactionSource)
                .clickable(onClick = onClick)
                .background(if (isHovered) LocalDSColors.current.hoverOverlay else Color.Transparent)
                .padding(start = DSLayout.sidebarHorizontalPadding, end = DSSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(DSLayout.chevronFrameWidth)
                    .clickable(onClick = onToggleExpand),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = LocalDSColors.current.textMuted,
                    modifier = Modifier.size(9.dp).rotate(chevronRotation),
                )
            }

            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = if (isActive) LocalDSColors.current.accentPrimary else LocalDSColors.current.gitModified,
                modifier = Modifier.size(DSFont.iconLG.value.dp),
            )

            Spacer(Modifier.width(DSSpacing.xs))

            Text(
                text = project.name,
                style = DSFont.sidebarItem,
                color = if (isActive) LocalDSColors.current.textPrimary else LocalDSColors.current.textSecondary,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            // Gear settings button — visible on hover
            if (isHovered) {
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(DSLayout.sidebarActionButtonSize),
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Project settings",
                        tint = LocalDSColors.current.textMuted,
                        modifier = Modifier.size(DSFont.iconBase.value.dp),
                    )
                }
            }
        }
    }
}

private fun revealInFinder(path: String) {
    try {
        Runtime.getRuntime().exec(arrayOf("open", "-R", path))
    } catch (_: Exception) { }
}

@Composable
private fun DirectoryRow(
    entry: DirectoryEntry,
    depth: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(150),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DSLayout.treeRowHeight)
            .hoverable(interactionSource)
            .clickable(onClick = onToggle)
            .background(if (isHovered) LocalDSColors.current.hoverOverlay else Color.Transparent)
            .padding(start = (depth * 16 + 4).dp, end = DSLayout.sidebarHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(DSLayout.chevronFrameWidth), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = LocalDSColors.current.textMuted,
                modifier = Modifier.size(9.dp).rotate(chevronRotation),
            )
        }
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            tint = LocalDSColors.current.textSecondary,
            modifier = Modifier.size(DSFont.iconLG.value.dp),
        )
        Spacer(Modifier.width(DSSpacing.xs))
        Text(
            text = entry.path.name,
            style = DSFont.sidebarItem,
            color = LocalDSColors.current.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FileRow(entry: FileEntry, depth: Int) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val ext = entry.path.name.substringAfterLast('.', "").lowercase()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DSLayout.treeRowHeight)
            .hoverable(interactionSource)
            .background(if (isHovered) LocalDSColors.current.hoverOverlay else Color.Transparent)
            .padding(start = (depth * 16 + 4).dp, end = DSLayout.sidebarHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(DSLayout.chevronFrameWidth))

        val (icon, iconColor) = fileIconAndColor(ext, LocalDSColors.current)
        Icon(
            icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(DSFont.iconLG.value.dp),
        )

        Spacer(Modifier.width(DSSpacing.xs))

        Text(
            text = entry.path.name,
            style = DSFont.sidebarItem,
            color = LocalDSColors.current.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        entry.gitStatus?.let { status ->
            Spacer(Modifier.width(DSSpacing.xs))
            Text(
                text = status.code,
                style = DSFont.gitStatus,
                color = gitStatusColor(status, LocalDSColors.current),
                modifier = Modifier.width(DSLayout.statusLetterWidth),
            )
        }
    }
}

private fun fileIconAndColor(ext: String, colors: DSColors): Pair<ImageVector, Color> = when (ext) {
    "kt", "kts"                              -> Icons.Default.Code to colors.accentPrimary
    "swift"                                  -> Icons.Default.Code to Color(0xFFF05138)
    "java"                                   -> Icons.Default.Code to Color(0xFFB07219)
    "json", "yaml", "yml", "toml"            -> Icons.Default.Settings to colors.textSecondary
    "md", "txt", "rst"                       -> Icons.Default.Description to colors.textSecondary
    "xml", "html", "css"                     -> Icons.Default.Code to colors.gitModified
    "gradle"                                 -> Icons.Default.Settings to colors.textSecondary
    "png", "jpg", "jpeg", "svg", "gif", "webp" -> Icons.Default.Image to colors.textSecondary
    else -> Icons.AutoMirrored.Filled.InsertDriveFile to colors.textSecondary
}

private fun gitStatusColor(status: GitFileStatus, colors: DSColors): Color = when (status) {
    GitFileStatus.MODIFIED  -> colors.gitModified
    GitFileStatus.ADDED     -> colors.gitAdded
    GitFileStatus.DELETED   -> colors.gitDeleted
    GitFileStatus.RENAMED   -> colors.gitRenamed
    GitFileStatus.COPIED    -> colors.gitAdded
    GitFileStatus.UNTRACKED -> colors.gitUntracked
}

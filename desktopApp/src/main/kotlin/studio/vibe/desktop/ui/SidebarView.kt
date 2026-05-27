@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.uuid.Uuid
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSLayout
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.shared.model.DirectoryEntry
import studio.vibe.shared.model.FileEntry
import studio.vibe.shared.model.FileTreeNode
import studio.vibe.shared.model.GitBranch
import studio.vibe.shared.model.GitFileStatus
import studio.vibe.shared.model.Project

private enum class SidebarTab { FILES, GIT, SPECS }

@Composable
fun SidebarView(
    container: DesktopServiceContainer,
    onToggleGitPanel: () -> Unit,
    onOpenProject: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val projects by container.projectStore.projects.collectAsState()
    val activeProjectId by container.projectStore.activeProjectId.collectAsState()
    var activeTab by remember { mutableStateOf(SidebarTab.FILES) }

    Row(modifier = modifier.background(DSColor.surfaceRaised)) {
        IconStrip(
            activeTab = activeTab,
            onTabSelected = { activeTab = it },
            onAddProject = onOpenProject,
        )

        Box(
            Modifier.width(1.dp).fillMaxHeight().background(DSColor.borderDefault),
        )

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            when (activeTab) {
                SidebarTab.FILES -> MultiProjectFileTree(
                    container = container,
                    projects = projects,
                    activeProjectId = activeProjectId,
                )
                SidebarTab.GIT -> GitBranchSection(container)
                SidebarTab.SPECS -> SpecsPlaceholder()
            }
        }
    }
}

// ── Icon Strip ───────────────────────────────────────────────────────────────

@Composable
private fun IconStrip(
    activeTab: SidebarTab,
    onTabSelected: (SidebarTab) -> Unit,
    onAddProject: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(DSLayout.iconStripWidth)
            .fillMaxHeight()
            .padding(top = DSSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
    ) {
        IconStripButton(
            icon = Icons.Default.Folder,
            contentDescription = "Files",
            isActive = activeTab == SidebarTab.FILES,
            onClick = { onTabSelected(SidebarTab.FILES) },
        )
        IconStripButton(
            icon = Icons.Default.AccountTree,
            contentDescription = "Git",
            isActive = activeTab == SidebarTab.GIT,
            onClick = { onTabSelected(SidebarTab.GIT) },
        )
        IconStripButton(
            icon = Icons.Default.Search,
            contentDescription = "Specs",
            isActive = activeTab == SidebarTab.SPECS,
            onClick = { onTabSelected(SidebarTab.SPECS) },
        )

        Spacer(Modifier.weight(1f))

        IconButton(
            onClick = onAddProject,
            modifier = Modifier.size(DSLayout.iconStripButtonSize),
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add project",
                tint = DSColor.textMuted,
                modifier = Modifier.size(DSFont.iconLG.value.dp),
            )
        }

        Spacer(Modifier.height(DSSpacing.sm))
    }
}

@Composable
private fun IconStripButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(DSLayout.iconStripButtonSize)
            .clip(RoundedCornerShape(DSRadius.sm))
            .background(if (isActive) DSColor.surfaceOverlay else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (isActive) DSColor.accentPrimary else DSColor.textMuted,
            modifier = Modifier.size(DSFont.iconLG.value.dp),
        )
    }
}

// ── Multi-Project File Tree ─────────────────────────────────────────────────

@Composable
private fun MultiProjectFileTree(
    container: DesktopServiceContainer,
    projects: List<Project>,
    activeProjectId: Uuid?,
) {
    // Track which projects are expanded
    var expandedProjects by remember { mutableStateOf(setOf<Uuid>()) }
    // Track expanded dirs within file trees
    var expandedDirs by remember { mutableStateOf(setOf<String>()) }
    // File tree cache per project
    var fileTrees by remember { mutableStateOf(mapOf<Uuid, List<FileTreeNode>>()) }

    // Auto-expand active project
    LaunchedEffect(activeProjectId) {
        if (activeProjectId != null && activeProjectId !in expandedProjects) {
            expandedProjects = expandedProjects + activeProjectId
        }
    }

    // Load file trees for expanded projects
    for (project in projects) {
        if (project.id in expandedProjects && project.id !in fileTrees) {
            LaunchedEffect(project.id) {
                val tree = container.fileTreeBuilder.buildTree(
                    root = project.path,
                    maxDepth = 5,
                )
                fileTrees = fileTrees + (project.id to tree)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(top = DSSpacing.sm),
    ) {
        for (project in projects) {
            val isActive = project.id == activeProjectId
            val isExpanded = project.id in expandedProjects

            item(key = "project-${project.id}") {
                ProjectHeaderRow(
                    project = project,
                    isActive = isActive,
                    isExpanded = isExpanded,
                    onClick = {
                        container.projectStore.setActiveProjectId(project.id)
                    },
                    onToggleExpand = {
                        expandedProjects = if (isExpanded) {
                            expandedProjects - project.id
                        } else {
                            expandedProjects + project.id
                        }
                    },
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
}

// Flatten tree for LazyColumn (avoids nested LazyColumn)
private data class FlatTreeItem(
    val id: String,
    val node: FileTreeNode,
    val depth: Int,
)

private fun flattenTree(
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
            .clickable(onClick = onClick)
            .background(if (isHovered) DSColor.hoverOverlay else Color.Transparent)
            .padding(horizontal = DSLayout.sidebarHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Chevron
        Box(
            modifier = Modifier
                .size(DSLayout.chevronFrameWidth)
                .clickable(onClick = onToggleExpand),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = DSColor.textMuted,
                modifier = Modifier.size(9.dp).rotate(chevronRotation),
            )
        }

        // Folder icon
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            tint = if (isActive) DSColor.accentPrimary else DSColor.gitModified,
            modifier = Modifier.size(DSFont.iconLG.value.dp),
        )

        Spacer(Modifier.width(DSSpacing.xs))

        // Project name
        Text(
            text = project.name,
            style = DSFont.sidebarItem,
            color = if (isActive) DSColor.textPrimary else DSColor.textSecondary,
            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
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
            .background(if (isHovered) DSColor.hoverOverlay else Color.Transparent)
            .padding(start = (depth * 16 + 4).dp, end = DSLayout.sidebarHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Chevron
        Box(
            modifier = Modifier.size(DSLayout.chevronFrameWidth),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = DSColor.textMuted,
                modifier = Modifier.size(9.dp).rotate(chevronRotation),
            )
        }

        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            tint = DSColor.textSecondary,
            modifier = Modifier.size(DSFont.iconLG.value.dp),
        )

        Spacer(Modifier.width(DSSpacing.xs))

        Text(
            text = entry.path.name,
            style = DSFont.sidebarItem,
            color = DSColor.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FileRow(
    entry: FileEntry,
    depth: Int,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val ext = entry.path.name.substringAfterLast('.', "").lowercase()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DSLayout.treeRowHeight)
            .hoverable(interactionSource)
            .background(if (isHovered) DSColor.hoverOverlay else Color.Transparent)
            .padding(start = (depth * 16 + 4).dp, end = DSLayout.sidebarHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Alignment spacer (no chevron for files)
        Spacer(Modifier.width(DSLayout.chevronFrameWidth))

        // File icon by extension
        val (icon, iconColor) = fileIconAndColor(ext)
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
            color = DSColor.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // Git status
        entry.gitStatus?.let { status ->
            Spacer(Modifier.width(DSSpacing.xs))
            Text(
                text = status.code,
                style = DSFont.gitStatus,
                color = gitStatusColor(status),
                modifier = Modifier.width(DSLayout.statusLetterWidth),
            )
        }
    }
}

private fun fileIconAndColor(ext: String): Pair<ImageVector, Color> = when (ext) {
    "kt", "kts" -> Icons.Default.Code to DSColor.accentPrimary
    "swift" -> Icons.Default.Code to Color(0xFFF05138)
    "java" -> Icons.Default.Code to Color(0xFFB07219)
    "json", "yaml", "yml", "toml" -> Icons.Default.Settings to DSColor.textSecondary
    "md", "txt", "rst" -> Icons.Default.Description to DSColor.textSecondary
    "xml", "html", "css" -> Icons.Default.Code to DSColor.gitModified
    "gradle" -> Icons.Default.Settings to DSColor.textSecondary
    "png", "jpg", "jpeg", "svg", "gif", "webp" -> Icons.Default.Image to DSColor.textSecondary
    else -> Icons.AutoMirrored.Filled.InsertDriveFile to DSColor.textSecondary
}

private fun gitStatusColor(status: GitFileStatus): Color = when (status) {
    GitFileStatus.MODIFIED -> DSColor.gitModified
    GitFileStatus.ADDED -> DSColor.gitAdded
    GitFileStatus.DELETED -> DSColor.gitDeleted
    GitFileStatus.RENAMED -> DSColor.gitRenamed
    GitFileStatus.COPIED -> DSColor.gitAdded
    GitFileStatus.UNTRACKED -> DSColor.gitUntracked
}

// ── Git Branch Section ───────────────────────────────────────────────────────

@Composable
private fun GitBranchSection(container: DesktopServiceContainer) {
    val gitState by container.gitSidebarViewModel.state.collectAsState()
    val activeProjectId by container.projectStore.activeProjectId.collectAsState()
    val projects by container.projectStore.projects.collectAsState()

    val activeProject = projects.find { it.id == activeProjectId }
    LaunchedEffect(activeProjectId) {
        if (activeProject != null && activeProjectId != null) {
            container.gitSidebarViewModel.loadGitInfo(activeProjectId!!, activeProject.path)
        }
    }

    val branches = activeProjectId?.let { gitState.projectBranches[it] } ?: emptyList()
    val status = activeProjectId?.let { gitState.projectGitStatuses[it] }
    val isNonGit = activeProjectId?.let { it in gitState.nonGitProjects } ?: false

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DSLayout.gitSectionHeaderHeight)
                .padding(horizontal = DSLayout.sidebarHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("GIT", style = DSFont.sidebarSection, color = DSColor.textSecondary)
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(DSColor.borderDefault))

        when {
            isNonGit -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Not a git repository", style = DSFont.sidebarItem, color = DSColor.textMuted)
                }
            }
            branches.isEmpty() && status == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading...", style = DSFont.sidebarItem, color = DSColor.textMuted)
                }
            }
            branches.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No branches", style = DSFont.sidebarItem, color = DSColor.textMuted)
                }
            }
            else -> {
                // Current branch at top
                val currentBranch = branches.find { it.isCurrent }
                if (currentBranch != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(DSLayout.treeRowHeight)
                            .background(DSColor.surfaceOverlay.copy(alpha = 0.5f))
                            .padding(horizontal = DSLayout.sidebarHorizontalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(DSColor.accentPrimary),
                        )
                        Spacer(Modifier.width(DSSpacing.xs))
                        Text(
                            text = currentBranch.name,
                            style = DSFont.sidebarItem,
                            color = DSColor.textPrimary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Other branches
                val otherBranches = branches.filter { !it.isCurrent }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(otherBranches, key = { it.name }) { branch ->
                        BranchRow(branch)
                    }
                }
            }
        }
    }
}

@Composable
private fun BranchRow(branch: GitBranch) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DSLayout.treeRowHeight)
            .hoverable(interactionSource)
            .background(if (isHovered) DSColor.surfaceOverlay else Color.Transparent)
            .padding(horizontal = DSLayout.sidebarHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(6.dp + DSSpacing.xs))

        Text(
            text = branch.name,
            style = DSFont.sidebarItem,
            color = DSColor.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (branch.isRemote) {
            Spacer(Modifier.width(DSSpacing.xs))
            Text(text = "remote", style = DSFont.badgeSmall, color = DSColor.textMuted)
        }
    }
}

// ── Specs Placeholder ────────────────────────────────────────────────────────

@Composable
private fun SpecsPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                tint = DSColor.textMuted,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.height(DSSpacing.sm))
            Text("CodeSpeak Specs", style = DSFont.sidebarItem, color = DSColor.textMuted)
        }
    }
}

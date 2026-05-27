@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
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
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.GitBranch
import studio.vibe.shared.model.GitFileStatus
import studio.vibe.shared.model.Project

private enum class SidebarTab { FILES, GIT, SPECS }

/**
 * Root sidebar composable.
 *
 * Houses the icon strip and main content column. The GIT tab now hosts
 * a full branch list with context menus and a "New Branch" action button
 * that surfaces a [CreateBranchSheet] dialog.
 */
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
                SidebarTab.GIT  -> GitBranchSection(container)
                SidebarTab.SPECS -> SpecsPlaceholder()
            }
        }
    }
}

// ── Icon Strip ────────────────────────────────────────────────────────────────

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

// ── Multi-Project File Tree ───────────────────────────────────────────────────

@Composable
private fun MultiProjectFileTree(
    container: DesktopServiceContainer,
    projects: List<Project>,
    activeProjectId: Uuid?,
) {
    var expandedProjects by remember { mutableStateOf(setOf<Uuid>()) }
    var expandedDirs by remember { mutableStateOf(setOf<String>()) }
    var fileTrees by remember { mutableStateOf(mapOf<Uuid, List<FileTreeNode>>()) }

    LaunchedEffect(activeProjectId) {
        if (activeProjectId != null && activeProjectId !in expandedProjects) {
            expandedProjects = expandedProjects + activeProjectId
        }
    }

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
                    onClick = { container.projectStore.setActiveProjectId(project.id) },
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

        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            tint = if (isActive) DSColor.accentPrimary else DSColor.gitModified,
            modifier = Modifier.size(DSFont.iconLG.value.dp),
        )

        Spacer(Modifier.width(DSSpacing.xs))

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
        Spacer(Modifier.width(DSLayout.chevronFrameWidth))

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
    "kt", "kts"                             -> Icons.Default.Code to DSColor.accentPrimary
    "swift"                                 -> Icons.Default.Code to Color(0xFFF05138)
    "java"                                  -> Icons.Default.Code to Color(0xFFB07219)
    "json", "yaml", "yml", "toml"           -> Icons.Default.Settings to DSColor.textSecondary
    "md", "txt", "rst"                      -> Icons.Default.Description to DSColor.textSecondary
    "xml", "html", "css"                    -> Icons.Default.Code to DSColor.gitModified
    "gradle"                                -> Icons.Default.Settings to DSColor.textSecondary
    "png", "jpg", "jpeg", "svg", "gif", "webp" -> Icons.Default.Image to DSColor.textSecondary
    else -> Icons.AutoMirrored.Filled.InsertDriveFile to DSColor.textSecondary
}

private fun gitStatusColor(status: GitFileStatus): Color = when (status) {
    GitFileStatus.MODIFIED  -> DSColor.gitModified
    GitFileStatus.ADDED     -> DSColor.gitAdded
    GitFileStatus.DELETED   -> DSColor.gitDeleted
    GitFileStatus.RENAMED   -> DSColor.gitRenamed
    GitFileStatus.COPIED    -> DSColor.gitAdded
    GitFileStatus.UNTRACKED -> DSColor.gitUntracked
}

// ── Git Branch Section ────────────────────────────────────────────────────────

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
    val gitStatus = activeProjectId?.let { gitState.projectGitStatuses[it] }
    val isNonGit = activeProjectId?.let { it in gitState.nonGitProjects } ?: false

    // State controlling the CreateBranchSheet dialog visibility
    var showCreateBranch by remember { mutableStateOf(false) }
    var createBranchFromBranch: String? by remember { mutableStateOf(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Section header
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
                    Text(
                        "Not a git repository",
                        style = DSFont.sidebarItem,
                        color = DSColor.textMuted,
                    )
                }
            }
            branches.isEmpty() && gitStatus == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading...", style = DSFont.sidebarItem, color = DSColor.textMuted)
                }
            }
            else -> {
                val localBranches = branches.filter { !it.isRemote }
                val remoteBranches = branches.filter { it.isRemote && !it.name.endsWith("/HEAD") }
                val currentBranch = localBranches.find { it.isCurrent }
                val aheadCount = gitStatus?.aheadCount ?: 0
                val behindCount = gitStatus?.behindCount ?: 0
                val remoteUnavailable = activeProjectId?.let {
                    it in gitState.remoteUnavailableProjects
                } ?: false

                LazyColumn(modifier = Modifier.weight(1f)) {
                    // Current branch row (highlighted)
                    if (currentBranch != null) {
                        item(key = "current-${currentBranch.name}") {
                            BranchRow(
                                branch = currentBranch,
                                aheadCount = aheadCount,
                                behindCount = behindCount,
                                projectPath = activeProject?.path,
                                onCheckout = null, // already current
                                onPull = { path ->
                                    activeProjectId?.let { id ->
                                        container.gitSidebarViewModel.gitBranchPull(
                                            projectId = id,
                                            branch = currentBranch.name,
                                            isCurrent = true,
                                            path = path,
                                        )
                                    }
                                },
                                onPush = { path ->
                                    activeProjectId?.let { id ->
                                        container.gitSidebarViewModel.gitBranchPush(
                                            projectId = id,
                                            branch = currentBranch.name,
                                            path = path,
                                        )
                                    }
                                },
                                onCreateFrom = {
                                    createBranchFromBranch = currentBranch.name
                                    showCreateBranch = true
                                },
                            )
                        }
                    }

                    // Other local branches
                    val otherLocal = localBranches.filter { !it.isCurrent }
                    items(otherLocal, key = { "local-${it.name}" }) { branch ->
                        BranchRow(
                            branch = branch,
                            aheadCount = 0,
                            behindCount = 0,
                            projectPath = activeProject?.path,
                            onCheckout = { path ->
                                activeProjectId?.let { id ->
                                    container.gitSidebarViewModel.checkout(id, branch.name, path)
                                }
                            },
                            onPull = { path ->
                                activeProjectId?.let { id ->
                                    container.gitSidebarViewModel.gitBranchPull(
                                        projectId = id,
                                        branch = branch.name,
                                        isCurrent = false,
                                        path = path,
                                    )
                                }
                            },
                            onPush = { path ->
                                activeProjectId?.let { id ->
                                    container.gitSidebarViewModel.gitBranchPush(
                                        projectId = id,
                                        branch = branch.name,
                                        path = path,
                                    )
                                }
                            },
                            onCreateFrom = {
                                createBranchFromBranch = branch.name
                                showCreateBranch = true
                            },
                        )
                    }

                    // Remote section separator
                    if (remoteUnavailable || remoteBranches.isNotEmpty()) {
                        item(key = "remote-separator") {
                            RemoteSectionSeparator()
                        }
                    }

                    if (remoteUnavailable) {
                        item(key = "remote-unavailable") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(DSLayout.gitFileRowHeight - 8.dp)
                                    .padding(horizontal = DSLayout.sidebarHorizontalPadding),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "unavailable",
                                    style = DSFont.sidebarItemSmall,
                                    color = DSColor.textMuted,
                                )
                            }
                        }
                    } else {
                        items(remoteBranches, key = { "remote-${it.name}" }) { branch ->
                            RemoteBranchRow(branch)
                        }
                    }

                    // "New Branch" action row — always at bottom of branch list
                    item(key = "new-branch-action") {
                        NewBranchRow(
                            onClick = {
                                createBranchFromBranch = null
                                showCreateBranch = true
                            },
                        )
                    }
                }
            }
        }
    }

    // CreateBranchSheet dialog
    if (showCreateBranch && activeProject != null && activeProjectId != null) {
        CreateBranchSheet(
            container = container,
            projectId = activeProjectId!!,
            projectPath = activeProject.path,
            fromBranch = createBranchFromBranch,
            onDismiss = {
                showCreateBranch = false
                createBranchFromBranch = null
            },
        )
    }
}

// ── Branch Rows ───────────────────────────────────────────────────────────────

@Composable
private fun BranchRow(
    branch: GitBranch,
    aheadCount: Int,
    behindCount: Int,
    projectPath: FilePath?,
    onCheckout: ((FilePath) -> Unit)?,
    onPull: (FilePath) -> Unit,
    onPush: (FilePath) -> Unit,
    onCreateFrom: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val menuItems = buildList {
        if (!branch.isCurrent && onCheckout != null) {
            add(
                ContextMenuItem("Checkout") {
                    projectPath?.let { onCheckout(it) }
                },
            )
        }
        add(
            ContextMenuItem("Pull") {
                projectPath?.let { onPull(it) }
            },
        )
        add(
            ContextMenuItem("Push") {
                projectPath?.let { onPush(it) }
            },
        )
        add(
            ContextMenuItem("Create Branch From This") {
                onCreateFrom()
            },
        )
    }

    ContextMenuArea(items = { menuItems }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DSLayout.gitFileRowHeight)
                .hoverable(interactionSource)
                .background(
                    when {
                        branch.isCurrent -> DSColor.surfaceOverlay.copy(alpha = 0.5f)
                        isHovered        -> DSColor.surfaceOverlay
                        else             -> Color.Transparent
                    },
                )
                .then(
                    if (!branch.isCurrent && onCheckout != null) {
                        Modifier.clickable { projectPath?.let { onCheckout(it) } }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = DSLayout.sidebarHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Current indicator dot
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (branch.isCurrent) DSColor.accentPrimary else Color.Transparent,
                    ),
            )

            Spacer(Modifier.width(DSSpacing.xs))

            Text(
                text = branch.name,
                style = DSFont.sidebarItem,
                color = if (branch.isCurrent) DSColor.textPrimary else DSColor.textSecondary,
                fontWeight = if (branch.isCurrent) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            // Ahead / behind counts — only for the current branch
            if (branch.isCurrent && (aheadCount > 0 || behindCount > 0)) {
                Spacer(Modifier.width(DSSpacing.xs))
                AheadBehindBadge(aheadCount = aheadCount, behindCount = behindCount)
            }
        }
    }
}

@Composable
private fun AheadBehindBadge(aheadCount: Int, behindCount: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(DSSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (aheadCount > 0) {
            Text(
                text = "\u2191$aheadCount",
                style = DSFont.gitAheadBehind,
                color = DSColor.gitAdded,
            )
        }
        if (behindCount > 0) {
            Text(
                text = "\u2193$behindCount",
                style = DSFont.gitAheadBehind,
                color = DSColor.gitDeleted,
            )
        }
    }
}

@Composable
private fun RemoteSectionSeparator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = DSSpacing.xs, horizontal = DSLayout.sidebarHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(DSColor.borderSubtle))
        Text("origin", style = DSFont.sidebarItemSmall, color = DSColor.textMuted)
        Box(Modifier.weight(1f).height(1.dp).background(DSColor.borderSubtle))
    }
}

@Composable
private fun RemoteBranchRow(branch: GitBranch) {
    val displayName = branch.name.removePrefix("origin/")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DSLayout.gitFileRowHeight - 6.dp)
            .padding(horizontal = DSLayout.sidebarHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(6.dp + DSSpacing.xs))

        Text(
            text = displayName,
            style = DSFont.sidebarItemSmall,
            color = DSColor.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        Spacer(Modifier.width(DSSpacing.xs))
        Text("remote", style = DSFont.badgeSmall, color = DSColor.textMuted)
    }
}

@Composable
private fun NewBranchRow(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DSLayout.gitFileRowHeight)
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .background(if (isHovered) DSColor.hoverOverlay else Color.Transparent)
            .padding(horizontal = DSLayout.sidebarHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            tint = DSColor.accentPrimary,
            modifier = Modifier.size(DSFont.iconBase.value.dp),
        )
        Spacer(Modifier.width(DSSpacing.xs))
        Text(
            "New branch",
            style = DSFont.sidebarItemSmall,
            color = DSColor.accentPrimary,
        )
    }
}

// ── Specs Placeholder ─────────────────────────────────────────────────────────

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

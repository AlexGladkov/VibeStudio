@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import studio.vibe.shared.contract.GitServicing
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.service.filetree.FileTreeBuilder
import studio.vibe.shared.viewmodel.GitSidebarViewModel
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSColors
import studio.vibe.desktop.ui.theme.LocalDSColors
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
import studio.vibe.shared.model.SpecStatus

private enum class SidebarTab { FILES, GIT, SPECS }

/** Tracks which project-level dialog is open, and for which project. */
private sealed class SidebarDialog {
    data class ProjectSettings(val project: Project) : SidebarDialog()
    data class GitRemoteSetup(val project: Project) : SidebarDialog()
}

/**
 * Root sidebar composable.
 *
 * Houses the icon strip and main content column. The GIT tab now hosts
 * a full branch list with context menus and a "New Branch" action button
 * that surfaces a [CreateBranchSheet] dialog.
 */
@Composable
fun SidebarView(
    projectStore: ProjectManaging,
    fileTreeBuilder: FileTreeBuilder,
    gitSidebarViewModel: GitSidebarViewModel,
    gitService: GitServicing,
    coroutineScope: CoroutineScope,
    onToggleGitPanel: () -> Unit,
    onOpenProject: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val projects by projectStore.projects.collectAsState()
    val activeProjectId by projectStore.activeProjectId.collectAsState()
    var activeTab by remember { mutableStateOf(SidebarTab.FILES) }

    Row(modifier = modifier.background(LocalDSColors.current.surfaceRaised)) {
        IconStrip(
            projectStore = projectStore,
            activeTab = activeTab,
            onTabSelected = { activeTab = it },
            onAddProject = onOpenProject,
        )

        Box(
            Modifier.width(1.dp).fillMaxHeight().background(LocalDSColors.current.borderDefault),
        )

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            when (activeTab) {
                SidebarTab.FILES -> MultiProjectFileTree(
                    projectStore = projectStore,
                    fileTreeBuilder = fileTreeBuilder,
                    gitService = gitService,
                    coroutineScope = coroutineScope,
                    projects = projects,
                    activeProjectId = activeProjectId,
                )
                SidebarTab.GIT  -> GitBranchSection(
                    projectStore = projectStore,
                    gitSidebarViewModel = gitSidebarViewModel,
                    gitService = gitService,
                    coroutineScope = coroutineScope,
                )
                SidebarTab.SPECS -> SpecsSection(
                    fileTreeBuilder = fileTreeBuilder,
                    projects = projects,
                    activeProjectId = activeProjectId,
                )
            }
        }
    }
}

// ── Icon Strip ────────────────────────────────────────────────────────────────

@Composable
private fun IconStrip(
    projectStore: ProjectManaging,
    activeTab: SidebarTab,
    onTabSelected: (SidebarTab) -> Unit,
    onAddProject: () -> Unit,
) {
    var showAddPopover by remember { mutableStateOf(false) }
    val colors = LocalDSColors.current

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

        Box {
            IconButton(
                onClick = { showAddPopover = true },
                modifier = Modifier.size(DSLayout.iconStripButtonSize),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add project",
                    tint = colors.textMuted,
                    modifier = Modifier.size(DSFont.iconLG.value.dp),
                )
            }

            DropdownMenu(
                expanded = showAddPopover,
                onDismissRequest = { showAddPopover = false },
            ) {
                AddProjectPopover(
                    projectStore = projectStore,
                    onOpenFolder = {
                        showAddPopover = false
                        onAddProject()
                    },
                    onDismiss = { showAddPopover = false },
                )
            }
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
            .background(if (isActive) LocalDSColors.current.surfaceOverlay else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (isActive) LocalDSColors.current.accentPrimary else LocalDSColors.current.textMuted,
            modifier = Modifier.size(DSFont.iconLG.value.dp),
        )
    }
}

// ── Multi-Project File Tree ───────────────────────────────────────────────────

@Composable
private fun MultiProjectFileTree(
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
    var activeDialog by remember { mutableStateOf<SidebarDialog?>(null) }

    LaunchedEffect(activeProjectId) {
        if (activeProjectId != null && activeProjectId !in expandedProjects) {
            expandedProjects = expandedProjects + activeProjectId
        }
    }

    for (project in projects) {
        if (project.id in expandedProjects && project.id !in fileTrees) {
            LaunchedEffect(project.id) {
                val tree = fileTreeBuilder.buildTree(
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
                    onClick = { projectStore.setActiveProjectId(project.id) },
                    onToggleExpand = {
                        expandedProjects = if (isExpanded) {
                            expandedProjects - project.id
                        } else {
                            expandedProjects + project.id
                        }
                    },
                    onOpenSettings = { activeDialog = SidebarDialog.ProjectSettings(project) },
                    onOpenGitRemote = { activeDialog = SidebarDialog.GitRemoteSetup(project) },
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

    // ── Dialogs ───────────────────────────────────────────────────────────────

    when (val dialog = activeDialog) {
        is SidebarDialog.ProjectSettings -> ProjectSettingsSheet(
            projectStore = projectStore,
            projectId = dialog.project.id,
            projectName = dialog.project.name,
            projectPath = dialog.project.path.path,
            onDismiss = { activeDialog = null },
        )
        is SidebarDialog.GitRemoteSetup -> GitRemoteSetupSheet(
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
    onOpenSettings: () -> Unit = {},
    onOpenGitRemote: () -> Unit = {},
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
    )

    ContextMenuArea(items = { menuItems }) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DSLayout.treeRowHeight)
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .background(if (isHovered) LocalDSColors.current.hoverOverlay else Color.Transparent)
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
    }
    } // end ContextMenuArea
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
        Box(
            modifier = Modifier.size(DSLayout.chevronFrameWidth),
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
    "kt", "kts"                             -> Icons.Default.Code to colors.accentPrimary
    "swift"                                 -> Icons.Default.Code to Color(0xFFF05138)
    "java"                                  -> Icons.Default.Code to Color(0xFFB07219)
    "json", "yaml", "yml", "toml"           -> Icons.Default.Settings to colors.textSecondary
    "md", "txt", "rst"                      -> Icons.Default.Description to colors.textSecondary
    "xml", "html", "css"                    -> Icons.Default.Code to colors.gitModified
    "gradle"                                -> Icons.Default.Settings to colors.textSecondary
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

// ── Git Branch Section ────────────────────────────────────────────────────────

@Composable
private fun GitBranchSection(
    projectStore: ProjectManaging,
    gitSidebarViewModel: GitSidebarViewModel,
    gitService: GitServicing,
    coroutineScope: CoroutineScope,
) {
    val gitState by gitSidebarViewModel.state.collectAsState()
    val activeProjectId by projectStore.activeProjectId.collectAsState()
    val projects by projectStore.projects.collectAsState()

    val activeProject = projects.find { it.id == activeProjectId }

    LaunchedEffect(activeProjectId) {
        if (activeProject != null && activeProjectId != null) {
            gitSidebarViewModel.loadGitInfo(activeProjectId!!, activeProject.path)
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
            Text("GIT", style = DSFont.sidebarSection, color = LocalDSColors.current.textSecondary)
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(LocalDSColors.current.borderDefault))

        when {
            isNonGit -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Not a git repository",
                        style = DSFont.sidebarItem,
                        color = LocalDSColors.current.textMuted,
                    )
                }
            }
            branches.isEmpty() && gitStatus == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading...", style = DSFont.sidebarItem, color = LocalDSColors.current.textMuted)
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
                                        gitSidebarViewModel.gitBranchPull(
                                            projectId = id,
                                            branch = currentBranch.name,
                                            isCurrent = true,
                                            path = path,
                                        )
                                    }
                                },
                                onPush = { path ->
                                    activeProjectId?.let { id ->
                                        gitSidebarViewModel.gitBranchPush(
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
                                    gitSidebarViewModel.checkout(id, branch.name, path)
                                }
                            },
                            onPull = { path ->
                                activeProjectId?.let { id ->
                                    gitSidebarViewModel.gitBranchPull(
                                        projectId = id,
                                        branch = branch.name,
                                        isCurrent = false,
                                        path = path,
                                    )
                                }
                            },
                            onPush = { path ->
                                activeProjectId?.let { id ->
                                    gitSidebarViewModel.gitBranchPush(
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
                                    color = LocalDSColors.current.textMuted,
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
            gitService = gitService,
            coroutineScope = coroutineScope,
            gitSidebarViewModel = gitSidebarViewModel,
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
                        branch.isCurrent -> LocalDSColors.current.surfaceOverlay.copy(alpha = 0.5f)
                        isHovered        -> LocalDSColors.current.surfaceOverlay
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
                        if (branch.isCurrent) LocalDSColors.current.accentPrimary else Color.Transparent,
                    ),
            )

            Spacer(Modifier.width(DSSpacing.xs))

            Text(
                text = branch.name,
                style = DSFont.sidebarItem,
                color = if (branch.isCurrent) LocalDSColors.current.textPrimary else LocalDSColors.current.textSecondary,
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
                color = LocalDSColors.current.gitAdded,
            )
        }
        if (behindCount > 0) {
            Text(
                text = "\u2193$behindCount",
                style = DSFont.gitAheadBehind,
                color = LocalDSColors.current.gitDeleted,
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
        Box(Modifier.weight(1f).height(1.dp).background(LocalDSColors.current.borderSubtle))
        Text("origin", style = DSFont.sidebarItemSmall, color = LocalDSColors.current.textMuted)
        Box(Modifier.weight(1f).height(1.dp).background(LocalDSColors.current.borderSubtle))
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
            color = LocalDSColors.current.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        Spacer(Modifier.width(DSSpacing.xs))
        Text("remote", style = DSFont.badgeSmall, color = LocalDSColors.current.textMuted)
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
            .background(if (isHovered) LocalDSColors.current.hoverOverlay else Color.Transparent)
            .padding(horizontal = DSLayout.sidebarHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            tint = LocalDSColors.current.accentPrimary,
            modifier = Modifier.size(DSFont.iconBase.value.dp),
        )
        Spacer(Modifier.width(DSSpacing.xs))
        Text(
            "New branch",
            style = DSFont.sidebarItemSmall,
            color = LocalDSColors.current.accentPrimary,
        )
    }
}

// ── Specs Section ─────────────────────────────────────────────────────────────

/**
 * Sidebar SPECS tab: shows all `.cs.md` files found in the active project's file
 * tree, flattened (no directory nesting). Each row displays a colored status dot
 * and the spec name derived from the file name.
 *
 * The file tree for the active project must already be loaded by
 * [MultiProjectFileTree]; we simply collect the filtered nodes here so that
 * no redundant I/O occurs.
 */
@Composable
private fun SpecsSection(
    fileTreeBuilder: FileTreeBuilder,
    projects: List<Project>,
    activeProjectId: Uuid?,
) {
    var fileTrees by remember { mutableStateOf(mapOf<Uuid, List<FileTreeNode>>()) }

    // Load the file tree for the active project if not yet available.
    val activeProject = projects.find { it.id == activeProjectId }
    if (activeProject != null && activeProjectId != null && activeProjectId !in fileTrees) {
        LaunchedEffect(activeProjectId) {
            val tree = fileTreeBuilder.buildTree(
                root = activeProject.path,
                maxDepth = 5,
            )
            fileTrees = fileTrees + (activeProjectId to tree)
        }
    }

    // Collect all .cs.md files from the active project tree, depth-first.
    val specEntries: List<FileEntry> = remember(fileTrees, activeProjectId) {
        val nodes = activeProjectId?.let { fileTrees[it] } ?: emptyList()
        collectSpecFiles(nodes)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DSLayout.gitSectionHeaderHeight)
                .padding(horizontal = DSLayout.sidebarHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("SPECS", style = DSFont.sidebarSection, color = LocalDSColors.current.textSecondary)
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(LocalDSColors.current.borderDefault))

        // ── Content ───────────────────────────────────────────────────────────
        when {
            activeProject == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No project selected",
                        style = DSFont.sidebarItem,
                        color = LocalDSColors.current.textMuted,
                    )
                }
            }
            activeProjectId !in fileTrees -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading...", style = DSFont.sidebarItem, color = LocalDSColors.current.textMuted)
                }
            }
            specEntries.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = LocalDSColors.current.textMuted,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.height(DSSpacing.sm))
                        Text(
                            "No specs found",
                            style = DSFont.sidebarItem,
                            color = LocalDSColors.current.textMuted,
                        )
                        Spacer(Modifier.height(DSSpacing.xxs))
                        Text(
                            "*.cs.md",
                            style = DSFont.sidebarItemSmall,
                            color = LocalDSColors.current.textDisabled,
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(
                        horizontal = DSLayout.sidebarHorizontalPadding,
                        vertical = DSSpacing.xs,
                    ),
                ) {
                    items(specEntries, key = { it.path.path }) { entry ->
                        SpecRow(entry = entry)
                    }
                }
            }
        }
    }
}

/**
 * Recursively collects all [FileEntry] nodes whose name ends in `.cs.md` from
 * the given tree, returning them in depth-first order with no directory nesting.
 */
private fun collectSpecFiles(nodes: List<FileTreeNode>): List<FileEntry> {
    val result = mutableListOf<FileEntry>()
    for (node in nodes) {
        when (node) {
            is FileTreeNode.File -> {
                if (node.entry.path.name.endsWith(".cs.md")) {
                    result.add(node.entry)
                }
            }
            is FileTreeNode.Directory -> {
                result.addAll(collectSpecFiles(node.entry.children))
            }
        }
    }
    return result
}

/**
 * A single spec-file row: status dot + spec name derived from the file name
 * by stripping the `.cs.md` suffix.
 *
 * [SpecStatus] is always [SpecStatus.UNKNOWN] at this layer — the sidebar only
 * shows discovered files. Run results are shown in the CodeSpeak mode view.
 */
@Composable
private fun SpecRow(entry: FileEntry) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    // Derive the display name: strip ".cs.md" or ".md" suffix.
    val specName = entry.path.name
        .removeSuffix(".md")
        .removeSuffix(".cs")

    // At the sidebar level we have no run results, so status is always unknown.
    val status = SpecStatus.UNKNOWN

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DSLayout.treeRowHeight)
            .hoverable(interactionSource)
            .background(if (isHovered) LocalDSColors.current.hoverOverlay else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Status indicator dot
        Box(
            modifier = Modifier
                .size(DSLayout.indicatorSize)
                .clip(RoundedCornerShape(50))
                .background(specStatusColor(status, LocalDSColors.current)),
        )

        Spacer(Modifier.width(DSSpacing.xs))

        // Spec name
        Text(
            text = specName,
            style = DSFont.sidebarItem,
            color = LocalDSColors.current.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Maps a [SpecStatus] to its display color following the Swift design system. */
private fun specStatusColor(status: SpecStatus, colors: DSColors): Color = when (status) {
    SpecStatus.PASSING -> colors.gitAdded
    SpecStatus.FAILING -> colors.gitDeleted
    SpecStatus.UNKNOWN -> colors.indicatorIdle
}

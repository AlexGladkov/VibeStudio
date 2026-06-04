@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui.sidebar

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import studio.vibe.shared.contract.GitServicing
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.desktop.ui.CreateBranchSheet
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSLayout
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.GitBranch
import studio.vibe.shared.viewmodel.GitSidebarViewModel

@Composable
internal fun GitBranchSection(
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

    var showCreateBranch by remember { mutableStateOf(false) }
    var createBranchFromBranch: String? by remember { mutableStateOf(null) }

    Column(modifier = Modifier.fillMaxSize()) {
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
                val remoteUnavailable = activeProjectId?.let { it in gitState.remoteUnavailableProjects } ?: false

                LazyColumn(modifier = Modifier.weight(1f)) {
                    if (currentBranch != null) {
                        item(key = "current-${currentBranch.name}") {
                            BranchRow(
                                branch = currentBranch,
                                aheadCount = aheadCount,
                                behindCount = behindCount,
                                projectPath = activeProject?.path,
                                onCheckout = null,
                                onPull = { path ->
                                    activeProjectId?.let { id ->
                                        gitSidebarViewModel.gitBranchPull(id, currentBranch.name, true, path)
                                    }
                                },
                                onPush = { path ->
                                    activeProjectId?.let { id ->
                                        gitSidebarViewModel.gitBranchPush(id, currentBranch.name, path)
                                    }
                                },
                                onCreateFrom = {
                                    createBranchFromBranch = currentBranch.name
                                    showCreateBranch = true
                                },
                            )
                        }
                    }

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
                                    gitSidebarViewModel.gitBranchPull(id, branch.name, false, path)
                                }
                            },
                            onPush = { path ->
                                activeProjectId?.let { id ->
                                    gitSidebarViewModel.gitBranchPush(id, branch.name, path)
                                }
                            },
                            onCreateFrom = {
                                createBranchFromBranch = branch.name
                                showCreateBranch = true
                            },
                        )
                    }

                    if (remoteUnavailable || remoteBranches.isNotEmpty()) {
                        item(key = "remote-separator") { RemoteSectionSeparator() }
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

                    item(key = "new-branch-action") {
                        NewBranchRow(onClick = {
                            createBranchFromBranch = null
                            showCreateBranch = true
                        })
                    }
                }
            }
        }
    }

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
            add(ContextMenuItem("Checkout") { projectPath?.let { onCheckout(it) } })
        }
        add(ContextMenuItem("Pull") { projectPath?.let { onPull(it) } })
        add(ContextMenuItem("Push") { projectPath?.let { onPush(it) } })
        add(ContextMenuItem("Create Branch From This") { onCreateFrom() })
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
                    } else Modifier
                )
                .padding(horizontal = DSLayout.sidebarHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (branch.isCurrent) LocalDSColors.current.accentPrimary else Color.Transparent),
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
            Text(text = "↑$aheadCount", style = DSFont.gitAheadBehind, color = LocalDSColors.current.gitAdded)
        }
        if (behindCount > 0) {
            Text(text = "↓$behindCount", style = DSFont.gitAheadBehind, color = LocalDSColors.current.gitDeleted)
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
        Text("New branch", style = DSFont.sidebarItemSmall, color = LocalDSColors.current.accentPrimary)
    }
}

@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import studio.vibe.shared.contract.GitServicing
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.viewmodel.GitSidebarViewModel
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSColors
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSLayout
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.shared.model.GitFile
import studio.vibe.shared.model.GitFileStatus

// ── GitPanel ──────────────────────────────────────────────────────────────────

@Composable
fun GitPanel(
    gitSidebarViewModel: GitSidebarViewModel,
    projectStore: ProjectManaging,
    gitService: GitServicing,
    coroutineScope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val gitState by gitSidebarViewModel.state.collectAsState()
    val activeProjectId by projectStore.activeProjectId.collectAsState()
    val projects by projectStore.projects.collectAsState()

    // Load git info when active project changes
    val activeProject = projects.find { it.id == activeProjectId }
    LaunchedEffect(activeProjectId) {
        if (activeProject != null && activeProjectId != null) {
            gitSidebarViewModel.loadGitInfo(activeProjectId!!, activeProject.path)
        }
    }

    val status = activeProjectId?.let { gitState.projectGitStatuses[it] }
    val stagedFiles = status?.stagedFiles ?: emptyList()
    val unstagedFiles = status?.unstagedFiles ?: emptyList()
    val untrackedFiles = status?.untrackedFiles ?: emptyList()
    val totalCount = stagedFiles.size + unstagedFiles.size + untrackedFiles.size

    // Diff sheet state — which file is open and whether it is staged
    var diffSheetFile by remember { mutableStateOf<GitFile?>(null) }
    var diffSheetStaged by remember { mutableStateOf(false) }

    Column(modifier = modifier.background(LocalDSColors.current.surfaceRaised)) {
        // Header: "CHANGES" + count badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DSLayout.gitSectionHeaderHeight)
                .padding(horizontal = DSLayout.sidebarHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "CHANGES",
                style = DSFont.sidebarSection,
                color = LocalDSColors.current.textSecondary,
            )
            if (totalCount > 0) {
                Box(
                    modifier = Modifier
                        .background(LocalDSColors.current.accentPrimary, RoundedCornerShape(50))
                        .padding(horizontal = DSSpacing.xs, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = totalCount.toString(),
                        style = DSFont.badgeSmall,
                        color = LocalDSColors.current.textInverse,
                    )
                }
            }
        }

        // 1px divider
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(LocalDSColors.current.borderDefault),
        )

        if (totalCount == 0) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = LocalDSColors.current.textMuted,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.height(DSSpacing.sm))
                    Text(
                        text = "Working tree clean",
                        style = DSFont.sidebarItem,
                        color = LocalDSColors.current.textMuted,
                    )
                }
            }
        } else {
            // File list with sections
            LazyColumn(modifier = Modifier.weight(1f)) {
                // Staged files
                if (stagedFiles.isNotEmpty()) {
                    item(key = "header-staged") {
                        SectionHeader("STAGED", stagedFiles.size, LocalDSColors.current.gitAdded)
                    }
                    items(stagedFiles, key = { "s-${it.path}" }) { file ->
                        GitFileRow(
                            file = file,
                            isStaged = true,
                            onOpenDiff = {
                                diffSheetFile = file
                                diffSheetStaged = true
                            },
                            onStageToggle = {
                                activeProject?.let { proj ->
                                    gitSidebarViewModel.let { vm ->
                                        // Unstage
                                        activeProjectId?.let { id ->
                                            val scope = coroutineScope
                                            scope.launch {
                                                runCatching {
                                                    gitService.unstage(
                                                        files = listOf(file.path),
                                                        at = proj.path,
                                                    )
                                                    vm.loadGitInfo(id, proj.path)
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                        )
                    }
                }

                // Unstaged files
                if (unstagedFiles.isNotEmpty()) {
                    item(key = "header-unstaged") {
                        SectionHeader("MODIFIED", unstagedFiles.size, LocalDSColors.current.gitModified)
                    }
                    items(unstagedFiles, key = { "u-${it.path}" }) { file ->
                        GitFileRow(
                            file = file,
                            isStaged = false,
                            onOpenDiff = {
                                diffSheetFile = file
                                diffSheetStaged = false
                            },
                            onStageToggle = {
                                activeProject?.let { proj ->
                                    activeProjectId?.let { id ->
                                        val scope = coroutineScope
                                        scope.launch {
                                            runCatching {
                                                gitService.stage(
                                                    files = listOf(file.path),
                                                    at = proj.path,
                                                )
                                                gitSidebarViewModel.loadGitInfo(id, proj.path)
                                            }
                                        }
                                    }
                                }
                            },
                        )
                    }
                }

                // Untracked files
                if (untrackedFiles.isNotEmpty()) {
                    item(key = "header-untracked") {
                        SectionHeader("UNTRACKED", untrackedFiles.size, LocalDSColors.current.gitUntracked)
                    }
                    items(untrackedFiles, key = { "t-${it.path}" }) { file ->
                        GitFileRow(
                            file = file,
                            isStaged = false,
                            onOpenDiff = {
                                diffSheetFile = file
                                diffSheetStaged = false
                            },
                            onStageToggle = {
                                activeProject?.let { proj ->
                                    activeProjectId?.let { id ->
                                        val scope = coroutineScope
                                        scope.launch {
                                            runCatching {
                                                gitService.stage(
                                                    files = listOf(file.path),
                                                    at = proj.path,
                                                )
                                                gitSidebarViewModel.loadGitInfo(id, proj.path)
                                            }
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }

            // Commit panel at bottom — delegates to CommitPanel composable
            if (activeProjectId != null && activeProject != null) {
                CommitPanel(
                    gitSidebarViewModel = gitSidebarViewModel,
                    projectId = activeProjectId!!,
                    projectPath = activeProject.path,
                )
            }
        }
    }

    // Diff sheet overlay — rendered outside the Column so it creates its own window
    val sheetFile = diffSheetFile
    if (sheetFile != null && activeProject != null) {
        FileDiffSheet(
            gitQuerying = gitService,
            file = sheetFile,
            projectPath = activeProject.path.path,
            staged = diffSheetStaged,
            onDismiss = { diffSheetFile = null },
        )
    }
}

// ── Section header ────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(label: String, count: Int, accentColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DSLayout.gitSectionHeaderHeight)
            .padding(horizontal = DSLayout.sidebarHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = DSFont.sidebarSection,
            color = LocalDSColors.current.textSecondary,
        )
        Spacer(Modifier.width(DSSpacing.xs))
        Text(
            text = count.toString(),
            style = DSFont.sidebarItemSmall,
            color = accentColor,
        )
    }
}

// ── File row ──────────────────────────────────────────────────────────────────

@Composable
private fun GitFileRow(
    file: GitFile,
    isStaged: Boolean,
    onOpenDiff: () -> Unit,
    onStageToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val contextMenuItems = listOf(
        ContextMenuItem("View Diff") { onOpenDiff() },
        ContextMenuItem(if (isStaged) "Unstage" else "Stage") { onStageToggle() },
    )

    ContextMenuArea(items = { contextMenuItems }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DSLayout.changesFileRowHeight)
                .hoverable(interactionSource)
                .background(if (isHovered) LocalDSColors.current.surfaceOverlay else Color.Transparent)
                .pointerInput(file.path) {
                    detectTapGestures(
                        onDoubleTap = { onOpenDiff() },
                    )
                }
                .padding(horizontal = DSLayout.sidebarHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Filename (basename only)
            Text(
                text = file.path.substringAfterLast('/'),
                style = DSFont.sidebarItem,
                color = LocalDSColors.current.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Spacer(Modifier.width(DSSpacing.xs))

            // Status letter
            Text(
                text = statusLetterFor(file.status),
                style = DSFont.gitStatus,
                color = statusColorFor(file.status, LocalDSColors.current),
                modifier = Modifier.width(DSLayout.statusLetterWidth),
            )
        }
    }
}

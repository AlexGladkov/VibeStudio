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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSLayout
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.shared.model.GitFile
import studio.vibe.shared.model.GitFileStatus

@Composable
fun GitPanel(
    container: DesktopServiceContainer,
    modifier: Modifier = Modifier,
) {
    val gitState by container.gitSidebarViewModel.state.collectAsState()
    val activeProjectId by container.projectStore.activeProjectId.collectAsState()
    val projects by container.projectStore.projects.collectAsState()

    // Load git info when active project changes
    val activeProject = projects.find { it.id == activeProjectId }
    LaunchedEffect(activeProjectId) {
        if (activeProject != null && activeProjectId != null) {
            container.gitSidebarViewModel.loadGitInfo(activeProjectId!!, activeProject.path)
        }
    }

    val status = activeProjectId?.let { gitState.projectGitStatuses[it] }
    val allFiles = buildList {
        status?.stagedFiles?.let { addAll(it) }
        status?.unstagedFiles?.let { addAll(it) }
        status?.untrackedFiles?.let { addAll(it) }
    }

    Column(modifier = modifier.background(DSColor.surfaceRaised)) {
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
                color = DSColor.textSecondary,
            )
            Box(
                modifier = Modifier
                    .background(DSColor.accentPrimary, RoundedCornerShape(50))
                    .padding(horizontal = DSSpacing.xs, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = allFiles.size.toString(),
                    style = DSFont.badgeSmall,
                    color = DSColor.textInverse,
                )
            }
        }

        // 1px divider
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DSColor.borderDefault),
        )

        if (allFiles.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = DSColor.textMuted,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.height(DSSpacing.sm))
                    Text(
                        text = "Working tree clean",
                        style = DSFont.sidebarItem,
                        color = DSColor.textMuted,
                    )
                }
            }
        } else {
            // File list
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(allFiles, key = { it.path }) { file ->
                    GitFileRow(file)
                }
            }
        }
    }
}

@Composable
private fun GitFileRow(file: GitFile) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DSLayout.changesFileRowHeight)
            .hoverable(interactionSource)
            .background(if (isHovered) DSColor.surfaceOverlay else Color.Transparent)
            .padding(horizontal = DSLayout.sidebarHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Filename
        Text(
            text = file.path.substringAfterLast('/'),
            style = DSFont.sidebarItem,
            color = DSColor.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        Spacer(Modifier.width(DSSpacing.xs))

        // Status letter
        Text(
            text = statusLetter(file.status),
            style = DSFont.gitStatus,
            color = statusColor(file.status),
            modifier = Modifier.width(DSLayout.statusLetterWidth),
        )
    }
}

private fun statusLetter(status: GitFileStatus): String = when (status) {
    GitFileStatus.MODIFIED -> "M"
    GitFileStatus.ADDED -> "A"
    GitFileStatus.DELETED -> "D"
    GitFileStatus.RENAMED -> "R"
    GitFileStatus.COPIED -> "C"
    GitFileStatus.UNTRACKED -> "?"
}

private fun statusColor(status: GitFileStatus): Color = when (status) {
    GitFileStatus.MODIFIED -> DSColor.gitModified
    GitFileStatus.ADDED -> DSColor.gitAdded
    GitFileStatus.DELETED -> DSColor.gitDeleted
    GitFileStatus.RENAMED -> DSColor.gitRenamed
    GitFileStatus.COPIED -> DSColor.gitAdded
    GitFileStatus.UNTRACKED -> DSColor.gitUntracked
}

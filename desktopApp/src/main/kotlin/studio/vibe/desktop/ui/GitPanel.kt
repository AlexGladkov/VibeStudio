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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
    val stagedFiles = status?.stagedFiles ?: emptyList()
    val unstagedFiles = status?.unstagedFiles ?: emptyList()
    val untrackedFiles = status?.untrackedFiles ?: emptyList()
    val totalCount = stagedFiles.size + unstagedFiles.size + untrackedFiles.size

    val commitSummary = activeProjectId?.let { gitState.commitSummaries[it] } ?: ""
    val isCommitting = activeProjectId?.let { it in gitState.committingProjects } ?: false
    val commitError = activeProjectId?.let { gitState.commitPanelErrors[it] }

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
            if (totalCount > 0) {
                Box(
                    modifier = Modifier
                        .background(DSColor.accentPrimary, RoundedCornerShape(50))
                        .padding(horizontal = DSSpacing.xs, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = totalCount.toString(),
                        style = DSFont.badgeSmall,
                        color = DSColor.textInverse,
                    )
                }
            }
        }

        // 1px divider
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DSColor.borderDefault),
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
            // File list with sections
            LazyColumn(modifier = Modifier.weight(1f)) {
                // Staged files
                if (stagedFiles.isNotEmpty()) {
                    item(key = "header-staged") {
                        SectionHeader("STAGED", stagedFiles.size, DSColor.gitAdded)
                    }
                    items(stagedFiles, key = { "s-${it.path}" }) { file ->
                        GitFileRow(file)
                    }
                }

                // Unstaged files
                if (unstagedFiles.isNotEmpty()) {
                    item(key = "header-unstaged") {
                        SectionHeader("MODIFIED", unstagedFiles.size, DSColor.gitModified)
                    }
                    items(unstagedFiles, key = { "u-${it.path}" }) { file ->
                        GitFileRow(file)
                    }
                }

                // Untracked files
                if (untrackedFiles.isNotEmpty()) {
                    item(key = "header-untracked") {
                        SectionHeader("UNTRACKED", untrackedFiles.size, DSColor.gitUntracked)
                    }
                    items(untrackedFiles, key = { "t-${it.path}" }) { file ->
                        GitFileRow(file)
                    }
                }
            }

            // Commit section at bottom
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(DSColor.borderDefault),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DSSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
            ) {
                OutlinedTextField(
                    value = commitSummary,
                    onValueChange = { value ->
                        activeProjectId?.let {
                            container.gitSidebarViewModel.updateCommitSummary(it, value)
                        }
                    },
                    placeholder = {
                        Text(
                            "Commit message...",
                            style = DSFont.commitInput,
                            color = DSColor.textMuted,
                        )
                    },
                    textStyle = DSFont.commitInput.copy(color = DSColor.textPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = DSLayout.commitInputMinHeight, max = DSLayout.commitInputMaxHeight),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DSColor.borderFocus,
                        unfocusedBorderColor = DSColor.borderDefault,
                        cursorColor = DSColor.accentPrimary,
                        focusedContainerColor = DSColor.surfaceInput,
                        unfocusedContainerColor = DSColor.surfaceInput,
                    ),
                    shape = RoundedCornerShape(DSRadius.md),
                )

                // Error message
                commitError?.let { err ->
                    Text(
                        text = err,
                        style = DSFont.sidebarItemSmall,
                        color = DSColor.gitDeleted,
                    )
                }

                Button(
                    onClick = {
                        if (activeProjectId != null && activeProject != null) {
                            container.gitSidebarViewModel.performCommit(
                                activeProjectId!!,
                                activeProject.path,
                            )
                        }
                    },
                    enabled = commitSummary.isNotBlank() && !isCommitting,
                    shape = RoundedCornerShape(DSRadius.md),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DSColor.buttonPrimaryBg,
                        contentColor = DSColor.buttonPrimaryText,
                        disabledContainerColor = DSColor.surfaceOverlay,
                        disabledContentColor = DSColor.textDisabled,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DSLayout.gitButtonHeight),
                ) {
                    Text(
                        text = if (isCommitting) "Committing..." else "Commit",
                        style = DSFont.buttonLabel,
                    )
                }
            }
        }
    }
}

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
            color = DSColor.textSecondary,
        )
        Spacer(Modifier.width(DSSpacing.xs))
        Text(
            text = count.toString(),
            style = DSFont.sidebarItemSmall,
            color = accentColor,
        )
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
        // Filename (basename only)
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

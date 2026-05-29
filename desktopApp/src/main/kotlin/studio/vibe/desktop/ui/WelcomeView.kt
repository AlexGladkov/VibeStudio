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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSLayout
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.Project

@Composable
fun WelcomeView(
    onOpenProject: () -> Unit,
    onCreateNew: () -> Unit = {},
    container: DesktopServiceContainer? = null,
    modifier: Modifier = Modifier,
) {
    val recentProjects = container?.projectStore?.recentProjects?.let {
        val state by it.collectAsState()
        state
    } ?: emptyList()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LocalDSColors.current.surfaceBase),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = DSLayout.welcomeListMaxWidth).padding(32.dp),
        ) {
            Spacer(Modifier.weight(1f))

            // Hero icon
            Icon(
                Icons.Default.Terminal,
                contentDescription = null,
                tint = LocalDSColors.current.accentPrimary,
                modifier = Modifier.size(48.dp),
            )

            Spacer(Modifier.height(DSSpacing.sm))

            // Title
            Text(
                text = "VibeStudio",
                style = DSFont.welcomeTitle,
                color = LocalDSColors.current.textPrimary,
            )

            Spacer(Modifier.height(DSSpacing.sm))

            // Subtitle
            Text(
                text = "Open a folder to get started",
                style = DSFont.sidebarItem,
                color = LocalDSColors.current.textSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(DSSpacing.xl))

            // Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
                // Primary: Create New
                Button(
                    onClick = onCreateNew,
                    shape = RoundedCornerShape(DSRadius.md),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalDSColors.current.buttonPrimaryBg,
                        contentColor = LocalDSColors.current.buttonPrimaryText,
                    ),
                    modifier = Modifier.height(28.dp),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(DSSpacing.xs))
                    Text(
                        text = "Create New",
                        style = DSFont.buttonLabel,
                        color = LocalDSColors.current.buttonPrimaryText,
                    )
                }

                // Secondary: Open Folder
                OutlinedButton(
                    onClick = onOpenProject,
                    shape = RoundedCornerShape(DSRadius.md),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LocalDSColors.current.borderDefault),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = LocalDSColors.current.surfaceOverlay,
                        contentColor = LocalDSColors.current.textPrimary,
                    ),
                    modifier = Modifier.height(28.dp),
                ) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(DSSpacing.xs))
                    Text(
                        text = "Open Folder",
                        style = DSFont.buttonLabel,
                        color = LocalDSColors.current.textPrimary,
                    )
                }
            }

            // Recent projects section
            if (recentProjects.isNotEmpty()) {
                Spacer(Modifier.height(DSSpacing.xl))

                Text(
                    text = "RECENT",
                    style = DSFont.sidebarSection,
                    color = LocalDSColors.current.textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = DSSpacing.xxs),
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(DSSpacing.xs),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    items(recentProjects, key = { it.id }) { project ->
                        RecentProjectRow(
                            project = project,
                            onClick = {
                                container?.projectStore?.addProject(project.path)
                            },
                        )
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RecentProjectRow(
    project: Project,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DSRadius.sm))
            .hoverable(interactionSource)
            .background(if (isHovered) LocalDSColors.current.hoverOverlay else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = DSSpacing.sm, vertical = DSSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            tint = LocalDSColors.current.gitModified,
            modifier = Modifier.size(DSFont.iconLG.value.dp),
        )

        Spacer(Modifier.width(DSSpacing.sm))

        Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.xxs)) {
            Text(
                text = project.name,
                style = DSFont.sidebarItem,
                color = LocalDSColors.current.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = project.path.path,
                style = DSFont.sidebarItemSmall,
                color = LocalDSColors.current.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import studio.vibe.shared.model.Project

private enum class SidebarTab { FILES, GIT, SEARCH }

@Composable
fun SidebarView(
    container: DesktopServiceContainer,
    onToggleGitPanel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val projects by container.projectStore.projects.collectAsState()
    val activeProjectId by container.projectStore.activeProjectId.collectAsState()
    var activeTab by remember { mutableStateOf(SidebarTab.FILES) }

    Row(modifier = modifier.background(DSColor.surfaceRaised)) {
        // Icon strip (left edge, 32dp)
        IconStrip(
            activeTab = activeTab,
            onTabSelected = { activeTab = it },
            onAddProject = { /* TODO: file picker */ },
        )

        // 1px vertical divider
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(DSColor.borderDefault),
        )

        // Content panel
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            when (activeTab) {
                SidebarTab.FILES -> ProjectFileSection(
                    projects = projects,
                    activeProjectId = activeProjectId,
                    onProjectSelected = { container.projectStore.setActiveProjectId(it.id) },
                )
                SidebarTab.GIT -> GitSidebarPlaceholder()
                SidebarTab.SEARCH -> SearchPlaceholder()
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
            contentDescription = "Search",
            isActive = activeTab == SidebarTab.SEARCH,
            onClick = { onTabSelected(SidebarTab.SEARCH) },
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

// ── Project + File Section ───────────────────────────────────────────────────

@Composable
private fun ProjectFileSection(
    projects: List<Project>,
    activeProjectId: Uuid?,
    onProjectSelected: (Project) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(top = DSSpacing.sm),
    ) {
        items(projects, key = { it.id.toString() }) { project ->
            val isActive = project.id == activeProjectId
            ProjectHeaderRow(
                project = project,
                isActive = isActive,
                onClick = { onProjectSelected(project) },
            )
        }
    }
}

@Composable
private fun ProjectHeaderRow(
    project: Project,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DSLayout.treeRowHeight)
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .background(
                when {
                    isHovered -> DSColor.hoverOverlay
                    else -> Color.Transparent
                },
            )
            .padding(horizontal = DSLayout.sidebarHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Chevron placeholder
        Box(Modifier.width(DSLayout.chevronFrameWidth))

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

// ── Placeholder sections ─────────────────────────────────────────────────────

@Composable
private fun GitSidebarPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Git branches",
            style = DSFont.sidebarItem,
            color = DSColor.textMuted,
        )
    }
}

@Composable
private fun SearchPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Search",
            style = DSFont.sidebarItem,
            color = DSColor.textMuted,
        )
    }
}

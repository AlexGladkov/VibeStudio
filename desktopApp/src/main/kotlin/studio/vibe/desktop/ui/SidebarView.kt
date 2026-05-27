@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.outlined.ChangeHistory
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.uuid.Uuid
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.ui.theme.ColorAccentBlue
import studio.vibe.desktop.ui.theme.ColorSurfaceContainer
import studio.vibe.desktop.ui.theme.ColorTextSecondary
import studio.vibe.shared.model.Project

/**
 * Left sidebar containing:
 *  - Section tabs: Projects / Files / Git
 *  - Project list with active indicator
 *  - Placeholder file tree and git branches sections
 */
@Composable
fun SidebarView(
    container: DesktopServiceContainer,
    onToggleGitPanel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val projects by container.projectStore.projects.collectAsState()
    val activeProjectId by container.projectStore.activeProjectId.collectAsState()

    var activeTab by remember { mutableStateOf(SidebarTab.PROJECTS) }

    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
    ) {
        // Tab bar
        SidebarTabBar(
            activeTab = activeTab,
            onTabSelected = { activeTab = it },
            onToggleGitPanel = onToggleGitPanel,
        )

        // Content for active tab
        when (activeTab) {
            SidebarTab.PROJECTS -> ProjectListSection(
                projects = projects,
                activeProjectId = activeProjectId,
                onProjectSelected = { container.projectStore.setActiveProjectId(it.id) },
                onAddProject = { /* TODO: file picker */ },
            )
            SidebarTab.FILES -> FilesPlaceholderSection()
            SidebarTab.GIT -> GitBranchesPlaceholderSection()
        }
    }
}

// ── Tab model ─────────────────────────────────────────────────────────────────

private enum class SidebarTab { PROJECTS, FILES, GIT }

// ── Tab bar ───────────────────────────────────────────────────────────────────

@Composable
private fun SidebarTabBar(
    activeTab: SidebarTab,
    onTabSelected: (SidebarTab) -> Unit,
    onToggleGitPanel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SidebarTabButton(
            icon = {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = "Projects",
                    modifier = Modifier.size(16.dp),
                )
            },
            selected = activeTab == SidebarTab.PROJECTS,
            onClick = { onTabSelected(SidebarTab.PROJECTS) },
            modifier = Modifier.weight(1f),
        )
        SidebarTabButton(
            icon = {
                Icon(
                    Icons.Default.AccountTree,
                    contentDescription = "Files",
                    modifier = Modifier.size(16.dp),
                )
            },
            selected = activeTab == SidebarTab.FILES,
            onClick = { onTabSelected(SidebarTab.FILES) },
            modifier = Modifier.weight(1f),
        )
        SidebarTabButton(
            icon = {
                Icon(
                    Icons.Outlined.ChangeHistory,
                    contentDescription = "Git",
                    modifier = Modifier.size(16.dp),
                )
            },
            selected = activeTab == SidebarTab.GIT,
            onClick = { onTabSelected(SidebarTab.GIT) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SidebarTabButton(
    icon: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onClick)
            .then(
                if (selected) Modifier.background(color = ColorAccentBlue.copy(alpha = 0.15f))
                else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = if (selected) Modifier
                .background(color = ColorAccentBlue, shape = RoundedCornerShape(3.dp))
                .padding(4.dp)
            else Modifier.padding(4.dp),
        ) {
            icon()
        }
    }
}

// ── Projects section ──────────────────────────────────────────────────────────

@Composable
private fun ProjectListSection(
    projects: List<Project>,
    activeProjectId: Uuid?,
    onProjectSelected: (Project) -> Unit,
    onAddProject: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Section header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "PROJECTS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = ColorTextSecondary,
                letterSpacing = 0.8.sp,
            )
            IconButton(
                onClick = onAddProject,
                modifier = Modifier.size(20.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add project",
                    tint = ColorTextSecondary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        // Project rows
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(projects, key = { it.id.toString() }) { project ->
                ProjectRow(
                    project = project,
                    isActive = project.id == activeProjectId,
                    onClick = { onProjectSelected(project) },
                )
            }
        }
    }
}

@Composable
private fun ProjectRow(
    project: Project,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clickable(onClick = onClick)
            .background(if (isActive) ColorSurfaceContainer else Color.Transparent)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Active indicator dot
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(ColorAccentBlue),
            )
            Spacer(Modifier.width(6.dp))
        } else {
            Spacer(Modifier.width(12.dp))
        }

        Text(
            text = project.name,
            fontSize = 13.sp,
            color = if (isActive) MaterialTheme.colorScheme.onSurface else ColorTextSecondary,
            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Files placeholder ─────────────────────────────────────────────────────────

@Composable
private fun FilesPlaceholderSection() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "File tree\npending",
            fontSize = 12.sp,
            color = ColorTextSecondary,
        )
    }
}

// ── Git branches placeholder ──────────────────────────────────────────────────

@Composable
private fun GitBranchesPlaceholderSection() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Git branches\npending",
            fontSize = 12.sp,
            color = ColorTextSecondary,
        )
    }
}

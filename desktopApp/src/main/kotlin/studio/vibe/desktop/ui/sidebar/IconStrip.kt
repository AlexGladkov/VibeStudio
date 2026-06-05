package studio.vibe.desktop.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.desktop.ui.AddProjectPopover
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSLayout
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.desktop.ui.theme.LocalDSColors

internal enum class SidebarTab { FILES, GIT, SPECS }

@Composable
internal fun IconStrip(
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
            icon = Icons.AutoMirrored.Filled.ManageSearch,
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

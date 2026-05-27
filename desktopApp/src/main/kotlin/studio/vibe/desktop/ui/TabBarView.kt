@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

@Composable
fun TabBarView(
    container: DesktopServiceContainer,
    onOpenProject: () -> Unit,
) {
    val projects by container.projectStore.projects.collectAsState()
    val activeProjectId by container.projectStore.activeProjectId.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DSLayout.tabBarHeight)
            .background(DSColor.surfaceTabBar)
            .padding(horizontal = DSSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DSLayout.tabGap),
    ) {
        // Project tabs
        projects.forEach { project ->
            TabItem(
                name = project.name,
                isActive = project.id == activeProjectId,
                onClick = { container.projectStore.setActiveProjectId(project.id) },
                onClose = {
                    container.projectStore.removeProject(project.id)
                },
            )
        }

        Spacer(Modifier.weight(1f))

        // Add tab button
        Box(
            modifier = Modifier
                .size(DSLayout.tabAddButtonSize)
                .clip(RoundedCornerShape(DSRadius.md))
                .clickable(onClick = onOpenProject),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "New tab",
                tint = DSColor.textSecondary,
                modifier = Modifier.size(DSFont.tabTitle.fontSize.value.dp),
            )
        }
    }
}

@Composable
private fun TabItem(
    name: String,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgColor = when {
        isActive -> DSColor.surfaceTabActive
        isHovered -> DSColor.surfaceTabHover
        else -> DSColor.surfaceTabInactive
    }

    Box {
        Row(
            modifier = Modifier
                .widthIn(min = DSLayout.tabMinWidth, max = DSLayout.tabMaxWidth)
                .height(DSLayout.tabHeight)
                .clip(RoundedCornerShape(topStart = DSRadius.md, topEnd = DSRadius.md))
                .background(bgColor)
                .hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(horizontal = DSLayout.tabHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Activity indicator dot
            Box(
                modifier = Modifier
                    .size(DSLayout.indicatorSize)
                    .clip(CircleShape)
                    .background(if (isActive) Color.Transparent else DSColor.indicatorIdle),
            )

            Spacer(Modifier.width(DSSpacing.xs))

            // Tab name
            Text(
                text = name,
                style = DSFont.tabTitle,
                color = if (isActive || isHovered) DSColor.textPrimary else DSColor.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            // Close button (visible on hover or active)
            if (isActive || isHovered) {
                Spacer(Modifier.width(DSSpacing.xxs))
                Box(
                    modifier = Modifier
                        .size(DSLayout.tabCloseSize)
                        .clip(RoundedCornerShape(DSRadius.sm))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close tab",
                        tint = DSColor.textSecondary,
                        modifier = Modifier.size(DSLayout.tabCloseIconSize.value.dp),
                    )
                }
            }
        }

        // Active bottom indicator
        if (isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(DSLayout.tabMinWidth)
                    .height(DSLayout.tabActiveIndicatorHeight)
                    .background(DSColor.accentPrimary),
            )
        }
    }
}

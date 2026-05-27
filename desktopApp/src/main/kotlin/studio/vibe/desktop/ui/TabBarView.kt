@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import studio.vibe.shared.model.TabActivityState

/**
 * Horizontal tab bar displaying one tab per open project.
 *
 * Enhancements over the initial implementation:
 * - Horizontal scroll when tabs overflow the available width.
 * - Per-tab activity dot driven by [TabActivityState] from the terminal service.
 *   idle → no dot / grey, running → green pulsing, waiting → yellow, error → red.
 * - Close button (×) that appears on hover or when the tab is active.
 * - "+" button at the far right opens the project picker.
 */
@Composable
fun TabBarView(
    container: DesktopServiceContainer,
    onOpenProject: () -> Unit,
) {
    val projects by container.projectStore.projects.collectAsState()
    val activeProjectId by container.projectStore.activeProjectId.collectAsState()
    val activityStates by container.terminalService.projectActivityStates.collectAsState()

    val scrollState: ScrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .height(DSLayout.tabBarHeight)
            .background(DSColor.surfaceTabBar),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Scrollable tab strip
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollState)
                .padding(horizontal = DSSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DSLayout.tabGap),
        ) {
            projects.forEach { project ->
                val activity = activityStates[project.id] ?: TabActivityState.IDLE
                TabItem(
                    name = project.name,
                    isActive = project.id == activeProjectId,
                    activityState = activity,
                    onClick = { container.projectStore.setActiveProjectId(project.id) },
                    onClose = { container.projectStore.removeProject(project.id) },
                )
            }
        }

        // Add / open project button — always visible at the right edge
        Box(
            modifier = Modifier
                .size(DSLayout.tabAddButtonSize)
                .clip(RoundedCornerShape(DSRadius.md))
                .clickable(onClick = onOpenProject),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "New project",
                tint = DSColor.textSecondary,
                modifier = Modifier.size(DSFont.tabTitle.fontSize.value.dp),
            )
        }

        Spacer(Modifier.width(DSSpacing.xs))
    }
}

// ── TabItem ───────────────────────────────────────────────────────────────────

@Composable
private fun TabItem(
    name: String,
    isActive: Boolean,
    activityState: TabActivityState,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgColor = when {
        isActive  -> DSColor.surfaceTabActive
        isHovered -> DSColor.surfaceTabHover
        else      -> DSColor.surfaceTabInactive
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
            ActivityDot(activityState)

            Spacer(Modifier.width(DSSpacing.xs))

            // Tab label
            Text(
                text = name,
                style = DSFont.tabTitle,
                color = if (isActive || isHovered) DSColor.textPrimary else DSColor.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            // Close button — only on hover or active
            if (isActive || isHovered) {
                Spacer(Modifier.width(DSSpacing.xxs))
                CloseButton(onClick = onClose)
            }
        }

        // Active bottom underline
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

// ── Activity dot ──────────────────────────────────────────────────────────────

@Composable
private fun ActivityDot(state: TabActivityState) {
    val dotColor: Color
    val shouldPulse: Boolean

    when (state) {
        TabActivityState.IDLE, TabActivityState.HIDDEN -> {
            dotColor = DSColor.indicatorIdle
            shouldPulse = false
        }
        TabActivityState.RUNNING -> {
            dotColor = DSColor.indicatorRunning
            shouldPulse = true
        }
        TabActivityState.WAITING_FOR_INPUT -> {
            dotColor = DSColor.indicatorWaiting
            shouldPulse = false
        }
        TabActivityState.ERROR -> {
            dotColor = DSColor.indicatorError
            shouldPulse = false
        }
    }

    val alpha = if (shouldPulse) {
        val infiniteTransition = rememberInfiniteTransition(label = "activity-pulse")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse-alpha",
        )
        pulseAlpha
    } else {
        if (state == TabActivityState.IDLE || state == TabActivityState.HIDDEN) 0f else 1f
    }

    Box(
        modifier = Modifier
            .size(DSLayout.indicatorSize)
            .clip(CircleShape)
            .background(dotColor.copy(alpha = alpha)),
    )
}

// ── Close button ──────────────────────────────────────────────────────────────

@Composable
private fun CloseButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .size(DSLayout.tabCloseSize)
            .clip(RoundedCornerShape(DSRadius.sm))
            .background(if (isHovered) DSColor.hoverOverlay else Color.Transparent)
            .hoverable(interactionSource)
            .clickable(onClick = onClick),
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

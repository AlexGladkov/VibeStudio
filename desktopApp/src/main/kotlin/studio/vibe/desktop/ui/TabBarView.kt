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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.uuid.Uuid
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.LocalDSColors
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
 * - Close confirmation dialog when a tab has an active terminal session.
 * - "+" button at the far right opens the project picker.
 * - Drag-to-reorder: drag a tab horizontally; positions are swapped live on drop.
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

    // ── Drag state shared across all tabs ─────────────────────────────────────
    // draggedId: which tab is being dragged right now.
    // dragOffset: cumulative horizontal drag in pixels for the dragged tab.
    // tabWidthPx: measured width of a single tab cell (set on first layout).
    var draggedId by remember { mutableStateOf<Uuid?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var tabWidthPx by remember { mutableStateOf(0f) }
    var showAddPopover by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .height(DSLayout.tabBarHeight)
            .background(LocalDSColors.current.surfaceTabBar),
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
            projects.forEachIndexed { index, project ->
                val activity = activityStates[project.id] ?: TabActivityState.IDLE
                val isDragging = draggedId == project.id

                TabItem(
                    name = project.name,
                    isActive = project.id == activeProjectId,
                    activityState = activity,
                    isDragging = isDragging,
                    dragOffsetX = if (isDragging) dragOffset else 0f,
                    anyDragActive = draggedId != null,
                    onClick = { container.projectStore.setActiveProjectId(project.id) },
                    onClose = {
                        container.terminalService.killAllSessions(project.id)
                        container.toolbarViewModel.cleanupProject(project.id)
                        container.projectStore.removeProject(project.id)
                    },
                    onDragStart = {
                        draggedId = project.id
                        dragOffset = 0f
                    },
                    onDrag = { delta ->
                        dragOffset += delta

                        // Swap when drag exceeds half a tab width in either direction
                        val threshold = tabWidthPx * 0.5f
                        if (tabWidthPx > 0f) {
                            when {
                                dragOffset > threshold && index < projects.lastIndex -> {
                                    container.projectStore.moveProjects(
                                        fromIndices = setOf(index),
                                        toDestination = index + 1,
                                    )
                                    dragOffset -= tabWidthPx
                                }
                                dragOffset < -threshold && index > 0 -> {
                                    container.projectStore.moveProjects(
                                        fromIndices = setOf(index),
                                        toDestination = index - 1,
                                    )
                                    dragOffset += tabWidthPx
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        draggedId = null
                        dragOffset = 0f
                    },
                    onWidthMeasured = { px -> if (tabWidthPx == 0f) tabWidthPx = px },
                )
            }
        }

        // Add / open project button — always visible at the right edge.
        // When at least one project is already open, clicking the button shows
        // the Recents popover (mirrors AddProjectPopover.swift on macOS). When
        // no projects are open yet, fall through directly to the folder
        // picker since the Welcome screen already handles discovery.
        Box {
            Box(
                modifier = Modifier
                    .size(DSLayout.tabAddButtonSize)
                    .clip(RoundedCornerShape(DSRadius.md))
                    .clickable {
                        if (projects.isEmpty()) {
                            onOpenProject()
                        } else {
                            showAddPopover = true
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "New project",
                    tint = LocalDSColors.current.textSecondary,
                    modifier = Modifier.size(DSFont.tabTitle.fontSize.value.dp),
                )
            }

            DropdownMenu(
                expanded = showAddPopover,
                onDismissRequest = { showAddPopover = false },
            ) {
                AddProjectPopover(
                    container = container,
                    onOpenFolder = {
                        showAddPopover = false
                        onOpenProject()
                    },
                    onDismiss = { showAddPopover = false },
                )
            }
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
    isDragging: Boolean,
    dragOffsetX: Float,
    anyDragActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (delta: Float) -> Unit,
    onDragEnd: () -> Unit,
    onWidthMeasured: (Float) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    // Close confirmation dialog: shown when the tab has an active session.
    var showCloseConfirm by remember { mutableStateOf(false) }

    val bgColor = when {
        isDragging -> LocalDSColors.current.surfaceTabActive
        isActive   -> LocalDSColors.current.surfaceTabActive
        isHovered  -> LocalDSColors.current.surfaceTabHover
        else       -> LocalDSColors.current.surfaceTabInactive
    }

    // Drop-zone highlight: the slot currently hovered by a foreign dragged tab
    // is shown with a slightly dimmed background so the user can see where the
    // dragged tab would land.
    val dropHighlight = anyDragActive && !isDragging && isHovered

    Box(
        modifier = Modifier
            .offset { IntOffset(dragOffsetX.roundToInt(), 0) }
            // Capture tab width for threshold calculations
            .onGloballyPositioned { coords ->
                onWidthMeasured(coords.size.width.toFloat())
            }
            .then(if (isDragging) Modifier.shadow(8.dp, RoundedCornerShape(DSRadius.md)) else Modifier)
            // Drag gesture
            .pointerInput(name) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                )
            },
    ) {
        Row(
            modifier = Modifier
                .widthIn(min = DSLayout.tabMinWidth, max = DSLayout.tabMaxWidth)
                .height(DSLayout.tabHeight)
                .clip(RoundedCornerShape(topStart = DSRadius.md, topEnd = DSRadius.md))
                .background(
                    if (dropHighlight) LocalDSColors.current.surfaceTabHover.copy(alpha = 0.6f)
                    else bgColor
                )
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
                color = if (isActive || isHovered) LocalDSColors.current.textPrimary else LocalDSColors.current.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            // Close button — only on hover or active
            if (isActive || isHovered) {
                Spacer(Modifier.width(DSSpacing.xxs))
                CloseButton(
                    onClick = {
                        val needsConfirmation = activityState != TabActivityState.IDLE &&
                            activityState != TabActivityState.HIDDEN
                        if (needsConfirmation) {
                            showCloseConfirm = true
                        } else {
                            onClose()
                        }
                    },
                )
            }
        }

        // Active bottom underline
        if (isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(DSLayout.tabMinWidth)
                    .height(DSLayout.tabActiveIndicatorHeight)
                    .background(LocalDSColors.current.accentPrimary),
            )
        }
    }

    // ── Close confirmation dialog ─────────────────────────────────────────────
    if (showCloseConfirm) {
        val activityLabel = when (activityState) {
            TabActivityState.RUNNING             -> "a running terminal session"
            TabActivityState.WAITING_FOR_INPUT   -> "a session waiting for input"
            TabActivityState.ERROR               -> "a session in an error state"
            else                                 -> "an active session"
        }
        AlertDialog(
            onDismissRequest = { showCloseConfirm = false },
            title = { Text("Close \"$name\"?") },
            text = {
                Text("This project has $activityLabel. Closing it will terminate all terminal processes.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCloseConfirm = false
                        onClose()
                    },
                ) {
                    Text("Close Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

// ── Activity dot ──────────────────────────────────────────────────────────────

@Composable
private fun ActivityDot(state: TabActivityState) {
    val dotColor: Color
    val shouldPulse: Boolean

    when (state) {
        TabActivityState.IDLE, TabActivityState.HIDDEN -> {
            dotColor = LocalDSColors.current.indicatorIdle
            shouldPulse = false
        }
        TabActivityState.RUNNING -> {
            dotColor = LocalDSColors.current.indicatorRunning
            shouldPulse = true
        }
        TabActivityState.WAITING_FOR_INPUT -> {
            dotColor = LocalDSColors.current.indicatorWaiting
            shouldPulse = false
        }
        TabActivityState.ERROR -> {
            dotColor = LocalDSColors.current.indicatorError
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
            .background(if (isHovered) LocalDSColors.current.hoverOverlay else Color.Transparent)
            .hoverable(interactionSource)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Close,
            contentDescription = "Close tab",
            tint = LocalDSColors.current.textSecondary,
            modifier = Modifier.size(DSLayout.tabCloseIconSize.value.dp),
        )
    }
}

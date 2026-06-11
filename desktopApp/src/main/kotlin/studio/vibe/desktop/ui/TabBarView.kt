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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
import studio.vibe.desktop.terminal.DesktopTerminalService
import studio.vibe.shared.feature.tabbar.presentation.FreeTabManaging
import studio.vibe.shared.core.common.project.ProjectManaging
import studio.vibe.shared.feature.settings.domain.model.GeneralPreferencesReading
import studio.vibe.shared.feature.toolbar.presentation.ToolbarViewModel
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSLayout
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import studio.vibe.shared.feature.tabbar.presentation.FreeTab
import studio.vibe.shared.core.common.terminal.TabActivityState

/**
 * Horizontal tab bar displaying one tab per open project, followed by free terminal tabs.
 *
 * Project tabs and free tabs are in separate drag groups — drag-reorder is only
 * possible within each group, not across the boundary.
 *
 * Per-tab activity dot:
 *   idle → hidden, running → green pulse, waitingForInput → yellow pulse+scale, error → red glow.
 *
 * Close confirmation dialog obeys [generalPreferences.confirmTabClose].
 *
 * "+" button at the far right creates a new free terminal tab.
 */
@Composable
fun TabBarView(
    projectStore: ProjectManaging,
    terminalService: DesktopTerminalService,
    toolbarViewModel: ToolbarViewModel,
    freeTabStore: FreeTabManaging,
    generalPreferences: GeneralPreferencesReading,
    onOpenProject: () -> Unit,
) {
    val projects by projectStore.projects.collectAsState()
    val activeProjectId by projectStore.activeProjectId.collectAsState()
    val activityStates by terminalService.projectActivityStates.collectAsState()
    val confirmClose by generalPreferences.confirmTabCloseFlow.collectAsState()
    val freeTabs by freeTabStore.freeTabsFlow.collectAsState()

    val scrollState: ScrollState = rememberScrollState()
    val tabBarScope = rememberCoroutineScope()

    // ── Drag state — project tabs ─────────────────────────────────────────────
    var draggedProjectId by remember { mutableStateOf<Uuid?>(null) }
    var dragProjectOffset by remember { mutableStateOf(0f) }
    var projectTabWidthPx by remember { mutableStateOf(0f) }

    // ── Drag state — free tabs ────────────────────────────────────────────────
    var draggedFreeTabId by remember { mutableStateOf<Uuid?>(null) }
    var dragFreeTabOffset by remember { mutableStateOf(0f) }
    var freeTabWidthPx by remember { mutableStateOf(0f) }

    var showAddPopover by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .height(DSLayout.tabBarHeight)
            .background(LocalDSColors.current.surfaceTabBar),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── Scrollable tab strip ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollState)
                .padding(horizontal = DSSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DSLayout.tabGap),
        ) {
            // ── Project tabs ──────────────────────────────────────────────────
            projects.forEachIndexed { index, project ->
                val activity = activityStates[project.id] ?: TabActivityState.IDLE
                val isDragging = draggedProjectId == project.id
                val needsConfirm = confirmClose &&
                    activity != TabActivityState.IDLE &&
                    activity != TabActivityState.HIDDEN

                TabItem(
                    name = project.name,
                    isActive = project.id == activeProjectId,
                    activityState = activity,
                    isDragging = isDragging,
                    dragOffsetX = if (isDragging) dragProjectOffset else 0f,
                    anyDragActive = draggedProjectId != null,
                    requiresCloseConfirmation = needsConfirm,
                    onClick = { projectStore.setActiveProjectId(project.id) },
                    onClose = {
                        terminalService.killAllSessions(project.id)
                        toolbarViewModel.cleanupProject(project.id)
                        tabBarScope.launch { projectStore.removeProject(project.id) }
                    },
                    onDragStart = {
                        draggedProjectId = project.id
                        dragProjectOffset = 0f
                    },
                    onDrag = { delta ->
                        dragProjectOffset += delta
                        val threshold = projectTabWidthPx * 0.5f
                        if (projectTabWidthPx > 0f) {
                            when {
                                dragProjectOffset > threshold && index < projects.lastIndex -> {
                                    tabBarScope.launch {
                                        projectStore.moveProjects(
                                            fromIndices = setOf(index),
                                            toDestination = index + 1,
                                        )
                                    }
                                    dragProjectOffset -= projectTabWidthPx
                                }
                                dragProjectOffset < -threshold && index > 0 -> {
                                    tabBarScope.launch {
                                        projectStore.moveProjects(
                                            fromIndices = setOf(index),
                                            toDestination = index - 1,
                                        )
                                    }
                                    dragProjectOffset += projectTabWidthPx
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        draggedProjectId = null
                        dragProjectOffset = 0f
                    },
                    onWidthMeasured = { px -> if (projectTabWidthPx == 0f) projectTabWidthPx = px },
                )
            }

            // ── Free tabs (separate group) ────────────────────────────────────
            if (freeTabs.isNotEmpty()) {
                // Thin separator between project and free tab groups
                Box(
                    Modifier
                        .width(1.dp)
                        .height(DSLayout.tabHeight * 0.6f)
                        .background(LocalDSColors.current.borderDefault),
                )
                Spacer(Modifier.width(DSLayout.tabGap))
            }

            freeTabs.forEachIndexed { index, freeTab ->
                val isDragging = draggedFreeTabId == freeTab.id
                FreeTabItemView(
                    title = freeTab.title,
                    isActive = freeTab.id == activeProjectId,
                    isDragging = isDragging,
                    dragOffsetX = if (isDragging) dragFreeTabOffset else 0f,
                    requiresCloseConfirmation = confirmClose,
                    onClick = { projectStore.setActiveProjectId(freeTab.id) },
                    onClose = { freeTabStore.removeFreeTab(freeTab.id) },
                    onRename = { newTitle ->
                        // FreeTab rename is in-memory only via store mutation
                        // FreeTabStoreImpl doesn't expose rename; done client-side via recreate
                        // For now: no-op (title stored in FreeTab is immutable in current model)
                    },
                    onDragStart = {
                        draggedFreeTabId = freeTab.id
                        dragFreeTabOffset = 0f
                    },
                    onDrag = { delta ->
                        dragFreeTabOffset += delta
                        val threshold = freeTabWidthPx * 0.5f
                        if (freeTabWidthPx > 0f) {
                            when {
                                dragFreeTabOffset > threshold && index < freeTabs.lastIndex -> {
                                    freeTabStore.moveFreeTabs(
                                        fromIndices = setOf(index),
                                        toDestination = index + 1,
                                    )
                                    dragFreeTabOffset -= freeTabWidthPx
                                }
                                dragFreeTabOffset < -threshold && index > 0 -> {
                                    freeTabStore.moveFreeTabs(
                                        fromIndices = setOf(index),
                                        toDestination = index - 1,
                                    )
                                    dragFreeTabOffset += freeTabWidthPx
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        draggedFreeTabId = null
                        dragFreeTabOffset = 0f
                    },
                    onWidthMeasured = { px -> if (freeTabWidthPx == 0f) freeTabWidthPx = px },
                )
            }
        }

        // ── Add project / new terminal buttons ────────────────────────────────
        Box {
            Box(
                modifier = Modifier
                    .size(DSLayout.tabAddButtonSize)
                    .clip(RoundedCornerShape(DSRadius.md))
                    .clickable { showAddPopover = true },
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
                    projectStore = projectStore,
                    onOpenFolder = {
                        showAddPopover = false
                        onOpenProject()
                    },
                    onDismiss = { showAddPopover = false },
                )
            }
        }

        // "New Terminal" — creates a free tab
        Box(
            modifier = Modifier
                .size(DSLayout.tabAddButtonSize)
                .clip(RoundedCornerShape(DSRadius.md))
                .clickable { freeTabStore.createFreeTab() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "New terminal",
                tint = LocalDSColors.current.textMuted,
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
    isDragging: Boolean,
    dragOffsetX: Float,
    anyDragActive: Boolean,
    requiresCloseConfirmation: Boolean = false,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (delta: Float) -> Unit,
    onDragEnd: () -> Unit,
    onWidthMeasured: (Float) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    // Close confirmation dialog: shown when the tab has an active session and pref enabled.
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
                        if (requiresCloseConfirmation) {
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
            TabActivityState.RUNNING             -> "запущенный терминальный сеанс"
            TabActivityState.WAITING_FOR_INPUT   -> "сеанс, ожидающий ввода"
            TabActivityState.ERROR               -> "сеанс в состоянии ошибки"
            else                                 -> "активный сеанс"
        }
        AlertDialog(
            onDismissRequest = { showCloseConfirm = false },
            title = { Text("Закрыть вкладку?") },
            text = {
                Text("Проект «$name» имеет $activityLabel. Закрытие завершит все терминальные процессы.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCloseConfirm = false
                        onClose()
                    },
                ) {
                    Text("Закрыть")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseConfirm = false }) {
                    Text("Отмена")
                }
            },
        )
    }
}

// ── Activity dot ──────────────────────────────────────────────────────────────

@Composable
private fun ActivityDot(state: TabActivityState) {
    // idle / hidden: dot invisible
    if (state == TabActivityState.IDLE || state == TabActivityState.HIDDEN) {
        Box(Modifier.size(DSLayout.indicatorSize))
        return
    }

    val dotColor = when (state) {
        TabActivityState.RUNNING           -> LocalDSColors.current.indicatorRunning
        TabActivityState.WAITING_FOR_INPUT -> LocalDSColors.current.indicatorWaiting
        TabActivityState.ERROR             -> LocalDSColors.current.indicatorError
        else                               -> LocalDSColors.current.indicatorIdle
    }

    val infiniteTransition = rememberInfiniteTransition(label = "activity-dot")

    // RUNNING: alpha pulse (0.4 → 1.0)
    // WAITING_FOR_INPUT: alpha pulse + scale pulse
    // ERROR: static red, 2s glow via shadow-like alpha
    val alpha by infiniteTransition.animateFloat(
        initialValue = if (state == TabActivityState.ERROR) 1f else 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (state == TabActivityState.ERROR) 2000 else 800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot-alpha",
    )

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state == TabActivityState.WAITING_FOR_INPUT) 1.4f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot-scale",
    )

    Box(
        modifier = Modifier
            .size(DSLayout.indicatorSize * scale)
            .clip(CircleShape)
            .background(dotColor.copy(alpha = alpha)),
    )
}

// ── Close button ──────────────────────────────────────────────────────────────

@Composable
private fun CloseButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    // The outer tab body installs `detectDragGestures` on its pointerInput
    // for drag-reorder. A child Box with `.clickable` is technically enough
    // for Compose to detect a tap, but the close hit-target is small (16dp)
    // and sits next to a region listening for horizontal drag — Skiko's
    // hit-testing on tiny clickable areas with no surrounding pointer
    // scope has been flaky in the past. We therefore explicitly install our
    // own pointerInput on the button and call `awaitEachGesture` with the
    // first down event consumed, which guarantees the parent drag detector
    // never sees this gesture and the click always fires.
    Box(
        modifier = Modifier
            .size(DSLayout.tabCloseSize)
            .clip(RoundedCornerShape(DSRadius.sm))
            .background(if (isHovered) LocalDSColors.current.hoverOverlay else Color.Transparent)
            .hoverable(interactionSource)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val up = waitForUpOrCancellation()
                    up?.consume()
                    if (up != null) onClick()
                }
            },
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

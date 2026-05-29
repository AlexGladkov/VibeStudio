@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.terminal.TerminalView
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.desktop.ui.theme.DSLayout
import java.awt.Cursor

/**
 * Regular-mode root layout: sidebar | center (tabs + terminal) | git panel.
 *
 * @param showGitPanel   When true the right-side git changes panel is shown.
 * @param showSidebar    When false the left sidebar and its resize handle are
 *                       hidden with a horizontal slide animation. Toggled via
 *                       the Cmd+B keyboard shortcut.
 * @param onToggleGitPanel Callback to flip [showGitPanel] in the parent.
 */
@Composable
fun RootView(
    container: DesktopServiceContainer,
    onOpenProject: () -> Unit,
    showGitPanel: Boolean,
    showSidebar: Boolean = true,
    onToggleGitPanel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeProject by container.projectStore.activeProjectId.collectAsState()
    val projects by container.projectStore.projects.collectAsState()
    var sidebarWidth by remember { mutableStateOf(DSLayout.sidebarDefaultWidth) }
    var gitPanelWidth by remember { mutableStateOf(DSLayout.changesPanelDefaultWidth) }
    val density = LocalDensity.current

    // ── File system watching ──────────────────────────────────────────────────
    // Restarts when the active project changes; stops when project is null.
    // Debounce (500 ms) is applied inside FileTreeViewModel.startWatching so
    // rapid burst saves don't trigger excessive tree reloads.
    LaunchedEffect(activeProject) {
        val projectPath = projects.find { it.id == activeProject }?.path
        if (projectPath != null) {
            container.fileTreeViewModel.loadTree(projectPath)
            container.fileTreeViewModel.startWatching(projectPath)
        } else {
            container.fileTreeViewModel.stopWatching()
        }
    }

    // ── Git status polling ────────────────────────────────────────────────────
    // Polls every 3 s (active) / 30 s (background) with exponential backoff.
    // showGitPanel is checked so we only poll at active rate when the panel is
    // visible; when hidden we fall back to background rate automatically via
    // GitStatusPollerImpl's isActive flag.
    LaunchedEffect(activeProject, showGitPanel) {
        val projectPath = projects.find { it.id == activeProject }?.path
        if (projectPath != null) {
            container.gitStatusPoller.startPolling(
                repository = projectPath,
                isActive = showGitPanel,
            )
        } else {
            container.gitStatusPoller.stopPolling()
        }
    }

    Row(modifier = modifier.fillMaxSize()) {
        // ── Sidebar (collapsible via Cmd+B) ──────────────────────────────────
        AnimatedVisibility(
            visible = showSidebar,
            enter = expandHorizontally(),
            exit = shrinkHorizontally(),
        ) {
            Row {
                SidebarView(
                    container = container,
                    onToggleGitPanel = onToggleGitPanel,
                    onOpenProject = onOpenProject,
                    modifier = Modifier
                        .width(sidebarWidth)
                        .fillMaxHeight(),
                )
                ResizeHandle(
                    onDrag = { delta ->
                        val newWidth = sidebarWidth + with(density) { delta.toDp() }
                        sidebarWidth = newWidth.coerceIn(180.dp, 400.dp)
                    },
                )
            }
        }

        // ── Center: Tab bar + terminal ────────────────────────────────────────
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            TabBarView(
                container = container,
                onOpenProject = onOpenProject,
            )

            // Terminal area — real pty4j + JediTerm
            if (activeProject != null) {
                val project = projects.find { it.id == activeProject }
                val toolbarState by container.toolbarViewModel.state.collectAsState()
                val terminalFontSize by container.generalPreferences.terminalFontSizeFlow.collectAsState()
                TerminalView(
                    service = container.terminalService,
                    projectId = activeProject!!,
                    targetSessionId = toolbarState.activeAgentSessionId,
                    terminalFontSize = terminalFontSize.toFloat(),
                    workingDirectory = project?.path?.path,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                TerminalPlaceholder(
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ── Git changes panel (right side) ────────────────────────────────────
        if (showGitPanel && activeProject != null) {
            ResizeHandle(
                onDrag = { delta ->
                    // Dragging right = shrink panel, dragging left = grow panel
                    val newWidth = gitPanelWidth - with(density) { delta.toDp() }
                    gitPanelWidth = newWidth.coerceIn(220.dp, 450.dp)
                },
            )

            GitPanel(
                container = container,
                modifier = Modifier
                    .width(gitPanelWidth)
                    .fillMaxHeight(),
            )
        }
    }
}

/**
 * Draggable resize handle between panels.
 * Shows as 5dp wide visible line with EW-resize cursor.
 */
@Composable
private fun ResizeHandle(
    onDrag: (Float) -> Unit,
) {
    Box(
        modifier = Modifier
            .width(5.dp)
            .fillMaxHeight()
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    onDrag(dragAmount.x)
                }
            }
            .background(LocalDSColors.current.borderDefault),
    )
}

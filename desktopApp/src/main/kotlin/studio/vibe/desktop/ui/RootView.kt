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
import kotlinx.coroutines.CoroutineScope
import studio.vibe.desktop.terminal.DesktopTerminalService
import studio.vibe.desktop.terminal.LocalTerminalRenderer
import studio.vibe.desktop.terminal.TerminalView
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.desktop.ui.theme.DSLayout
import studio.vibe.shared.contract.GitServicing
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.contract.FreeTabManaging
import studio.vibe.shared.preferences.GeneralPreferences
import studio.vibe.shared.service.filetree.FileTreeBuilder
import studio.vibe.shared.service.git.GitStatusPollerImpl
import studio.vibe.shared.viewmodel.FileTreeViewModel
import studio.vibe.shared.viewmodel.GitSidebarViewModel
import studio.vibe.shared.viewmodel.ToolbarViewModel
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
    projectStore: ProjectManaging,
    fileTreeBuilder: FileTreeBuilder,
    fileTreeViewModel: FileTreeViewModel,
    gitStatusPoller: GitStatusPollerImpl,
    gitSidebarViewModel: GitSidebarViewModel,
    gitService: GitServicing,
    toolbarViewModel: ToolbarViewModel,
    terminalService: DesktopTerminalService,
    freeTabStore: FreeTabManaging,
    generalPreferences: GeneralPreferences,
    coroutineScope: CoroutineScope,
    onOpenProject: () -> Unit,
    showGitPanel: Boolean,
    showSidebar: Boolean = true,
    onToggleGitPanel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeProject by projectStore.activeProjectId.collectAsState()
    val projects by projectStore.projects.collectAsState()
    var sidebarWidth by remember { mutableStateOf(DSLayout.sidebarDefaultWidth) }
    var gitPanelWidth by remember { mutableStateOf(DSLayout.changesPanelDefaultWidth) }
    val density = LocalDensity.current

    // ── File system watching ──────────────────────────────────────────────────
    LaunchedEffect(activeProject) {
        val projectPath = projects.find { it.id == activeProject }?.path
        if (projectPath != null) {
            fileTreeViewModel.loadTree(projectPath)
            fileTreeViewModel.startWatching(projectPath)
        } else {
            fileTreeViewModel.stopWatching()
        }
    }

    // ── Git status polling ────────────────────────────────────────────────────
    LaunchedEffect(activeProject, showGitPanel) {
        val projectPath = projects.find { it.id == activeProject }?.path
        if (projectPath != null) {
            gitStatusPoller.startPolling(
                repository = projectPath,
                isActive = showGitPanel,
            )
        } else {
            gitStatusPoller.stopPolling()
        }
    }

    Row(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = showSidebar,
            enter = expandHorizontally(),
            exit = shrinkHorizontally(),
        ) {
            Row {
                SidebarView(
                    projectStore = projectStore,
                    fileTreeBuilder = fileTreeBuilder,
                    gitSidebarViewModel = gitSidebarViewModel,
                    gitService = gitService,
                    coroutineScope = coroutineScope,
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

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            TabBarView(
                projectStore = projectStore,
                terminalService = terminalService,
                toolbarViewModel = toolbarViewModel,
                freeTabStore = freeTabStore,
                generalPreferences = generalPreferences,
                onOpenProject = onOpenProject,
            )

            if (activeProject != null) {
                val project = projects.find { it.id == activeProject }
                val toolbarState by toolbarViewModel.state.collectAsState()
                val terminalFontSize by generalPreferences.terminalFontSizeFlow.collectAsState()
                LocalTerminalRenderer.current.Render(
                    service = terminalService,
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

        if (showGitPanel && activeProject != null) {
            ResizeHandle(
                onDrag = { delta ->
                    val newWidth = gitPanelWidth - with(density) { delta.toDp() }
                    gitPanelWidth = newWidth.coerceIn(220.dp, 450.dp)
                },
            )

            GitPanel(
                gitSidebarViewModel = gitSidebarViewModel,
                projectStore = projectStore,
                gitService = gitService,
                coroutineScope = coroutineScope,
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

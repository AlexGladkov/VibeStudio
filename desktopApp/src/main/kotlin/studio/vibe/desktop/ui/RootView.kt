@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSLayout
import java.awt.Cursor

@Composable
fun RootView(
    container: DesktopServiceContainer,
    onOpenProject: () -> Unit,
    showGitPanel: Boolean,
    onToggleGitPanel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeProject by container.projectStore.activeProjectId.collectAsState()
    var sidebarWidth by remember { mutableStateOf(DSLayout.sidebarDefaultWidth) }
    var gitPanelWidth by remember { mutableStateOf(DSLayout.changesPanelDefaultWidth) }
    val density = LocalDensity.current

    Row(modifier = modifier.fillMaxSize()) {
        // Sidebar
        SidebarView(
            container = container,
            onToggleGitPanel = onToggleGitPanel,
            onOpenProject = onOpenProject,
            modifier = Modifier
                .width(sidebarWidth)
                .fillMaxHeight(),
        )

        // Sidebar resize handle
        ResizeHandle(
            onDrag = { delta ->
                val newWidth = sidebarWidth + with(density) { delta.toDp() }
                sidebarWidth = newWidth.coerceIn(180.dp, 400.dp)
            },
        )

        // Center: Tab bar + terminal
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            TabBarView(
                container = container,
                onOpenProject = onOpenProject,
            )

            TerminalPlaceholder(
                modifier = Modifier.weight(1f),
            )
        }

        // Git changes panel (right side)
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
 * Shows as 1px visible line with ~8px hit area and EW-resize cursor.
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
            .background(DSColor.borderDefault),
    )
}

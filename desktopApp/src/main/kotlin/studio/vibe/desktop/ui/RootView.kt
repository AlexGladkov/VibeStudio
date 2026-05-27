@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import studio.vibe.desktop.DesktopServiceContainer

/**
 * Main three-column IDE layout:
 *   [Sidebar 240dp] | [Terminal area ∞] | [Git panel 280dp — optional]
 *
 * The right git panel visibility is toggled via [showGitPanel].
 * Terminal integration (pty4j) is a placeholder until the pty layer is wired in.
 */
@Composable
fun RootView(
    container: DesktopServiceContainer,
    modifier: Modifier = Modifier,
) {
    val activeProject by container.projectStore.activeProjectId.collectAsState()
    var showGitPanel by remember { mutableStateOf(false) }

    Row(modifier = modifier.fillMaxSize()) {

        // Left sidebar: project list + file tree + git branches
        SidebarView(
            container = container,
            onToggleGitPanel = { showGitPanel = !showGitPanel },
            modifier = Modifier
                .width(240.dp)
                .fillMaxHeight(),
        )

        VerticalDivider()

        // Center: terminal area (pty4j integration pending)
        TerminalPlaceholder(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )

        // Right panel: git changes (optional, toggled from sidebar)
        if (showGitPanel && activeProject != null) {
            VerticalDivider()
            GitPanel(
                container = container,
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight(),
            )
        }
    }
}

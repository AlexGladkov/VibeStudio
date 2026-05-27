@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSLayout

@Composable
fun RootView(
    container: DesktopServiceContainer,
    modifier: Modifier = Modifier,
) {
    val activeProject by container.projectStore.activeProjectId.collectAsState()
    var showGitPanel by remember { mutableStateOf(false) }

    Row(modifier = modifier.fillMaxSize()) {
        SidebarView(
            container = container,
            onToggleGitPanel = { showGitPanel = !showGitPanel },
            modifier = Modifier
                .width(DSLayout.sidebarDefaultWidth)
                .fillMaxHeight(),
        )

        // 1px border between sidebar and content
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(DSColor.borderDefault),
        )

        // Tab bar + terminal area
        TerminalPlaceholder(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )

        if (showGitPanel && activeProject != null) {
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(DSColor.borderDefault),
            )
            GitPanel(
                container = container,
                modifier = Modifier
                    .width(DSLayout.changesPanelDefaultWidth)
                    .fillMaxHeight(),
            )
        }
    }
}

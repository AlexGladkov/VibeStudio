@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.ui.theme.DSColor

@Composable
fun VibeStudioDesktopApp(container: DesktopServiceContainer) {
    val projects by container.projectStore.projects.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DSColor.surfaceBase),
    ) {
        ToolbarView(container)

        if (projects.isEmpty()) {
            WelcomeView(
                onOpenProject = { /* TODO: wire AWT file chooser */ },
                modifier = Modifier.weight(1f),
            )
        } else {
            RootView(
                container = container,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import studio.vibe.desktop.DesktopServiceContainer

/**
 * Root composable for the VibeStudio Desktop application.
 *
 * Decides whether to show the welcome screen (no projects) or the main IDE layout.
 * The [DesktopServiceContainer] is the single source of truth for all services/VMs.
 */
@Composable
fun VibeStudioDesktopApp(container: DesktopServiceContainer) {
    val projects by container.projectStore.projects.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ToolbarView(container)

        if (projects.isEmpty()) {
            WelcomeView(
                onOpenProject = {
                    // TODO: wire AWT file chooser to add project
                    // container.projectStore.addProject(FilePath(chosen))
                },
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

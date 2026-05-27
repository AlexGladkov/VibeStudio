package studio.vibe.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import studio.vibe.desktop.ui.VibeStudioDesktopApp
import studio.vibe.desktop.ui.theme.VibeStudioTheme

fun main() = application {
    val serviceContainer = remember { DesktopServiceContainer() }

    Window(
        onCloseRequest = {
            serviceContainer.dispose()
            exitApplication()
        },
        title = "VibeStudio",
        state = rememberWindowState(width = 1280.dp, height = 800.dp),
        undecorated = false,
    ) {
        VibeStudioTheme {
            VibeStudioDesktopApp(serviceContainer)
        }
    }
}

package studio.vibe.desktop

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.vibe.desktop.ui.VibeStudioDesktopApp
import studio.vibe.desktop.ui.theme.DSLayout
import studio.vibe.desktop.ui.theme.VibeStudioTheme
import studio.vibe.shared.model.FilePath
import java.awt.Dimension
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

fun main() = application {
    val serviceContainer = remember { DesktopServiceContainer() }

    Window(
        onCloseRequest = {
            serviceContainer.dispose()
            exitApplication()
        },
        title = "VibeStudio",
        state = rememberWindowState(
            size = DpSize(DSLayout.windowDefaultWidth, DSLayout.windowDefaultHeight),
        ),
        undecorated = false,
    ) {
        // Enforce minimum window size
        window.minimumSize = Dimension(960, 600)

        val scope = rememberCoroutineScope()

        MenuBar {
            Menu("File") {
                Item(
                    "Open Folder...",
                    shortcut = KeyShortcut(Key.O, meta = true),
                    onClick = {
                        scope.launch {
                            val dir = pickFolder()
                            if (dir != null) {
                                serviceContainer.projectStore.addProject(FilePath(dir))
                            }
                        }
                    },
                )
                Separator()
                Item(
                    "Close Window",
                    shortcut = KeyShortcut(Key.W, meta = true),
                    onClick = {
                        serviceContainer.dispose()
                        exitApplication()
                    },
                )
            }
        }

        VibeStudioTheme {
            VibeStudioDesktopApp(serviceContainer)
        }
    }
}

/**
 * Open a native folder picker dialog (AWT FileDialog in directory mode).
 */
private suspend fun pickFolder(): String? = withContext(Dispatchers.IO) {
    System.setProperty("apple.awt.fileDialogForDirectories", "true")
    try {
        val dialog = FileDialog(null as Frame?, "Open Project Folder", FileDialog.LOAD)
        dialog.isVisible = true
        val dir = dialog.directory
        val file = dialog.file
        if (dir != null && file != null) {
            val selected = File(dir, file)
            if (selected.isDirectory) selected.absolutePath else dir
        } else {
            null
        }
    } finally {
        System.setProperty("apple.awt.fileDialogForDirectories", "false")
    }
}

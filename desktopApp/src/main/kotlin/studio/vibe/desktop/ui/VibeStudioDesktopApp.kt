@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.shared.model.FilePath
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
fun VibeStudioDesktopApp(container: DesktopServiceContainer) {
    val projects by container.projectStore.projects.collectAsState()
    val scope = rememberCoroutineScope()

    val openFolderPicker: () -> Unit = {
        scope.launch {
            val dir = pickFolder()
            if (dir != null) {
                container.projectStore.addProject(FilePath(dir))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DSColor.surfaceBase),
    ) {
        if (projects.isEmpty()) {
            // Swift: toolbar NOT shown on welcome screen
            WelcomeView(
                onOpenProject = openFolderPicker,
                onCreateNew = openFolderPicker, // reuse folder picker for now
                modifier = Modifier.weight(1f),
            )
        } else {
            // Toolbar only in IDE mode
            ToolbarView(container)
            RootView(
                container = container,
                onOpenProject = openFolderPicker,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Open a native folder picker dialog (AWT FileDialog in directory mode).
 * Returns the selected directory path, or null if cancelled.
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

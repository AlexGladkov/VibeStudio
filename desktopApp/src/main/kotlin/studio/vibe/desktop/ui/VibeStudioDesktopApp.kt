@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.shared.model.AIAssistant
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.ProjectManagerError
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Root application composable.
 *
 * App-level toggle state ([showGitPanel], [showSettings], [showSidebar],
 * [isCodeSpeakMode]) is hoisted to [Main.kt] so that [MenuBar] shortcuts and
 * this composable share the same source of truth. All toggle callbacks are
 * passed in from the caller.
 *
 * Additionally, [onPreviewKeyEvent] on the root [Box] provides a secondary
 * keyboard shortcut path for when focus is inside the Compose tree (e.g. when
 * no menu is open). Both paths converge on the same state mutations.
 *
 * Shortcuts (Cmd on macOS, Win/Meta on Linux/Windows):
 * - Cmd+B         — toggle sidebar
 * - Cmd+Shift+G   — toggle git panel
 * - Cmd+,         — open settings
 * - Cmd+Shift+C   — toggle CodeSpeak mode
 */
@Composable
fun VibeStudioDesktopApp(
    container: DesktopServiceContainer,
    showGitPanel: Boolean = false,
    showSettings: Boolean = false,
    showSidebar: Boolean = true,
    isCodeSpeakMode: Boolean = false,
    onToggleGitPanel: () -> Unit = {},
    onToggleSettings: (Boolean) -> Unit = {},
    onToggleSidebar: () -> Unit = {},
    onToggleCodeSpeakMode: () -> Unit = {},
    /** @deprecated Pass [onOpenFolder] from caller; currently unused stub. */
    onOpenFolder: () -> Unit = {},
) {
    val projects by container.projectStore.projects.collectAsState()
    val scope = rememberCoroutineScope()

    var agentToInstall by remember { mutableStateOf<AIAssistant?>(null) }

    // FocusRequester lets the root Box capture keyboard events via onPreviewKeyEvent.
    val focusRequester = remember { FocusRequester() }

    val openFolderPicker: () -> Unit = {
        scope.launch {
            val dir = pickFolder()
            if (dir != null) {
                try {
                    val project = container.projectStore.addProject(FilePath(dir))
                    container.projectStore.setActiveProjectId(project.id)
                } catch (e: ProjectManagerError.Duplicate) {
                    container.projectStore.setActiveProjectId(e.existingId)
                } catch (_: ProjectManagerError) { /* ignore */ }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusTarget()
            .onPreviewKeyEvent { event ->
                // Secondary shortcut path: fires when focus is inside Compose,
                // before any child handles the event. Returns true to consume.
                if (event.type == KeyEventType.KeyDown && event.isMetaPressed) {
                    when {
                        event.key == Key.B && !event.isShiftPressed -> {
                            onToggleSidebar()
                            true
                        }
                        event.key == Key.G && event.isShiftPressed -> {
                            onToggleGitPanel()
                            true
                        }
                        event.key == Key.Comma && !event.isShiftPressed -> {
                            onToggleSettings(true)
                            true
                        }
                        event.key == Key.C && event.isShiftPressed -> {
                            onToggleCodeSpeakMode()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DSColor.surfaceBase),
        ) {
            if (projects.isEmpty()) {
                WelcomeView(
                    onOpenProject = openFolderPicker,
                    onCreateNew = openFolderPicker,
                    container = container,
                    modifier = Modifier.weight(1f),
                )
            } else {
                ToolbarView(
                    container = container,
                    isCodeSpeakMode = isCodeSpeakMode,
                    onOpenSettings = { onToggleSettings(true) },
                    onToggleCodeSpeakMode = onToggleCodeSpeakMode,
                    onInstallAgent = { agentToInstall = it },
                )
                if (isCodeSpeakMode) {
                    CodeSpeakModeView(
                        container = container,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    RootView(
                        container = container,
                        onOpenProject = openFolderPicker,
                        showGitPanel = showGitPanel,
                        showSidebar = showSidebar,
                        onToggleGitPanel = onToggleGitPanel,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    // Settings dialog — rendered above the content stack as a floating overlay
    if (showSettings) {
        SettingsView(
            container = container,
            onDismiss = { onToggleSettings(false) },
        )
    }

    // Install agent sheet — shown when user selects an unavailable agent
    agentToInstall?.let { assistant ->
        InstallAgentSheet(
            assistant = assistant,
            onDismiss = { agentToInstall = null },
        )
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

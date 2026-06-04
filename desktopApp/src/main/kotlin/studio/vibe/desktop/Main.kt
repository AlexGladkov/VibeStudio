@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.vibe.desktop.ui.VibeStudioDesktopApp
import studio.vibe.shared.coordinator.AppMode
import studio.vibe.desktop.ui.theme.DSLayout
import studio.vibe.desktop.ui.theme.VibeStudioTheme
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.ProjectManagerError
import studio.vibe.shared.preferences.AppTheme
import java.awt.Dimension
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

fun main() = application {
    val serviceContainer = remember { DesktopServiceContainer() }

    // ── Startup ───────────────────────────────────────────────────────────────
    val startupScope = rememberCoroutineScope()
    val navigationCoordinator = serviceContainer.navigationCoordinator
    remember(serviceContainer) {
        AppLifecycleCoordinator(
            projectManaging = serviceContainer.projectStore,
            codeSpeakService = serviceContainer.codeSpeakService,
            navigationCoordinator = navigationCoordinator,
            restoreSessionUseCase = serviceContainer.restoreSessionUseCase,
            scope = startupScope,
        ).onStartup()
        true // remember requires a non-Unit return value
    }

    // ── App-level UI state — all driven by navigationCoordinator as single
    //    source of truth. MenuBar callbacks mutate coordinator; composables read
    //    coordinator flows. ──────────────────────────────────────────────────────
    val currentMode by navigationCoordinator.currentMode.collectAsState()
    val isCodeSpeakMode = currentMode == AppMode.CodeSpeak
    val showGitPanel by navigationCoordinator.showingChangesPanel.collectAsState()
    val showSettings by navigationCoordinator.showingSettings.collectAsState()
    val showSidebar by navigationCoordinator.showingSidebar.collectAsState()

    // ── Theme preference — collected reactively so palette switches as soon
    //    as the user picks a different option in Settings. ─────────────────────
    val appTheme by serviceContainer.generalPreferences.themeFlow.collectAsState()

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
        // Enforce minimum window size via AWT
        window.minimumSize = Dimension(960, 600)

        val scope = rememberCoroutineScope()

        MenuBar {
            // ── File ─────────────────────────────────────────────────────────
            Menu("File") {
                Item(
                    "Open Folder...",
                    shortcut = KeyShortcut(Key.O, meta = true),
                    onClick = {
                        scope.launch {
                            val dir = pickFolder()
                            if (dir != null) {
                                try {
                                    val project = serviceContainer.projectStore.addProject(FilePath(dir))
                                    serviceContainer.projectStore.setActiveProjectId(project.id)
                                } catch (e: ProjectManagerError.Duplicate) {
                                    serviceContainer.projectStore.setActiveProjectId(e.existingId)
                                } catch (_: ProjectManagerError) { /* ignore */ }
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

            // ── View ──────────────────────────────────────────────────────────
            Menu("View") {
                Item(
                    if (showSidebar) "Hide Sidebar" else "Show Sidebar",
                    shortcut = KeyShortcut(Key.B, meta = true),
                    onClick = { navigationCoordinator.toggleSidebar() },
                )
                Item(
                    if (showGitPanel) "Hide Git Panel" else "Show Git Panel",
                    shortcut = KeyShortcut(Key.G, meta = true, shift = true),
                    onClick = { navigationCoordinator.setShowingChangesPanel(!showGitPanel) },
                )
                Separator()
                Item(
                    if (isCodeSpeakMode) "Exit CodeSpeak Mode" else "Enter CodeSpeak Mode",
                    shortcut = KeyShortcut(Key.C, meta = true, shift = true),
                    onClick = { navigationCoordinator.syncMode(!isCodeSpeakMode) },
                )
            }

            // ── Preferences ───────────────────────────────────────────────────
            Menu("Preferences") {
                Item(
                    "Settings...",
                    shortcut = KeyShortcut(Key.Comma, meta = true),
                    onClick = { navigationCoordinator.setShowingSettings(true) },
                )
            }
        }

        val isDark = when (appTheme) {
            AppTheme.SYSTEM -> isSystemInDarkTheme()
            AppTheme.DARK   -> true
            AppTheme.LIGHT  -> false
        }

        VibeStudioTheme(isDark = isDark) {
            VibeStudioDesktopApp(
                container = serviceContainer,
                showGitPanel = showGitPanel,
                showSettings = showSettings,
                showSidebar = showSidebar,
                isCodeSpeakMode = isCodeSpeakMode,
                onToggleGitPanel = { navigationCoordinator.setShowingChangesPanel(!showGitPanel) },
                onToggleSettings = { navigationCoordinator.setShowingSettings(it) },
                onToggleSidebar = { navigationCoordinator.toggleSidebar() },
                onToggleCodeSpeakMode = { navigationCoordinator.syncMode(!isCodeSpeakMode) },
            )
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

@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package studio.vibe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
import studio.vibe.shared.core.common.AgentSessionRecord
import studio.vibe.desktop.ui.components.ResolvedSessionRow
import studio.vibe.desktop.ui.components.agentBrandColor
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.shared.core.common.assistant.ResumeRequest
import kotlin.uuid.Uuid
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.core.common.project.ProjectManagerError
import studio.vibe.shared.feature.settings.data.AppTheme
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
    val activeProjectId by container.projectStore.activeProjectId.collectAsState()
    val scope = rememberCoroutineScope()

    // Collect recent sessions for the active project (top 5).
    // flatMapLatest switches to the new project's flow whenever activeProjectId changes.
    val recentSessionRecords by remember(container) {
        container.projectStore.activeProjectId
            .flatMapLatest { projectId ->
                if (projectId == null) flowOf(emptyList())
                else container.agentSessionDataSource.recentSessionsFor(projectId, limit = 5)
            }
    }.collectAsState(initial = emptyList())

    // Resolve agent display name + brand color (requires LocalDSColors — composable context).
    val dsColors = LocalDSColors.current
    val recentSessions: List<ResolvedSessionRow> = recentSessionRecords.map { record ->
        val agent = container.agentRegistry.byId(record.agentId)
        ResolvedSessionRow(
            record = record,
            agentDisplayName = agent?.displayName ?: record.agentId,
            agentColor = agentBrandColor(record.agentId, dsColors),
        )
    }

    // Most-recent resumable session for the terminal banner (within last 24h, must have native id).
    val resumableSession: ResolvedSessionRow? = recentSessions.firstOrNull {
        it.record.agentNativeSessionId != null
    }

    val handleResumeSession: (AgentSessionRecord) -> Unit = { record ->
        scope.launch {
            val resumeRequest = record.agentNativeSessionId?.let { ResumeRequest(it) }
            container.assistantLauncher.start(
                projectId = Uuid.parse(record.projectId),
                agentId = record.agentId,
                resume = resumeRequest,
            )
        }
    }

    val agentToInstall by container.navigationCoordinator.agentToInstall.collectAsState()

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
                .background(LocalDSColors.current.surfaceBase),
        ) {
            if (projects.isEmpty()) {
                WelcomeView(
                    onOpenProject = openFolderPicker,
                    onCreateNew = openFolderPicker,
                    projectStore = container.projectStore,
                    recentSessions = recentSessions,
                    onResumeSession = handleResumeSession,
                    modifier = Modifier.weight(1f),
                )
            } else {
                ToolbarView(
                    toolbarViewModel = container.toolbarViewModel,
                    remoteControlServer = container.remoteControlServer,
                    projectStore = container.projectStore,
                    navigationCoordinator = container.navigationCoordinator,
                    isCodeSpeakMode = isCodeSpeakMode,
                    onOpenSettings = { onToggleSettings(true) },
                    onToggleCodeSpeakMode = onToggleCodeSpeakMode,
                    onToggleChangesPanel = onToggleGitPanel,
                    showingChangesPanel = showGitPanel,
                    onInstallAgent = { container.navigationCoordinator.setAgentToInstall(it) },
                )
                if (isCodeSpeakMode) {
                    CodeSpeakModeView(
                        persistenceStore = container.persistenceStore,
                        projectStore = container.projectStore,
                        runSpecBuildUseCase = container.runSpecBuildUseCase,
                        coroutineScope = container.scope,
                        codeSpeakPreferences = container.codeSpeakPreferences,
                        navigationCoordinator = container.navigationCoordinator,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    RootView(
                        projectStore = container.projectStore,
                        fileTreeBuilder = container.fileTreeBuilder,
                        fileTreeViewModel = container.fileTreeViewModel,
                        gitStatusPoller = container.gitStatusPoller,
                        gitSidebarViewModel = container.gitSidebarViewModel,
                        gitService = container.gitService,
                        toolbarViewModel = container.toolbarViewModel,
                        terminalService = container.terminalService,
                        freeTabStore = container.freeTabStore,
                        generalPreferences = container.generalPreferences,
                        coroutineScope = container.scope,
                        onOpenProject = openFolderPicker,
                        showGitPanel = showGitPanel,
                        showSidebar = showSidebar,
                        onToggleGitPanel = onToggleGitPanel,
                        resumableSession = resumableSession,
                        onResumeSession = handleResumeSession,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    // Settings dialog — rendered above the content stack as a floating overlay
    if (showSettings) {
        SettingsView(
            generalPreferences = container.generalPreferences,
            codeSpeakPreferences = container.codeSpeakPreferences,
            remoteControlPreferences = container.remoteControlPreferences,
            remoteControlServer = container.remoteControlServer,
            agentRegistry = container.agentRegistry,
            onDismiss = { onToggleSettings(false) },
        )
    }

    // Install agent sheet — shown when user selects an unavailable agent
    agentToInstall?.let { agent ->
        InstallAgentSheet(
            agent = agent,
            onDismiss = { container.navigationCoordinator.setAgentToInstall(null) },
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

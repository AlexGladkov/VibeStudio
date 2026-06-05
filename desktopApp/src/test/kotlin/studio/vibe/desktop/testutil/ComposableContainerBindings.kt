@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.testutil

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.ui.AddProjectPopover
import studio.vibe.desktop.ui.CodeSpeakModeView
import studio.vibe.desktop.ui.CommitPanel
import studio.vibe.desktop.ui.GitPanel
import studio.vibe.desktop.ui.RootView
import studio.vibe.desktop.ui.SettingsView
import studio.vibe.desktop.ui.SidebarView
import studio.vibe.desktop.ui.TabBarView
import studio.vibe.desktop.ui.TerminalAreaView
import studio.vibe.desktop.ui.ToolbarView
import studio.vibe.desktop.ui.TraceabilityPanelView
import studio.vibe.desktop.ui.WelcomeView
import studio.vibe.desktop.ui.settings.ClaudeSettingsPane
import studio.vibe.desktop.ui.settings.CodeSpeakSettingsPane
import studio.vibe.desktop.ui.settings.GeneralSettingsPane
import studio.vibe.desktop.ui.settings.LlmSettingsPane
import studio.vibe.desktop.ui.settings.RemoteControlSettingsPane
import studio.vibe.shared.contract.AIAgent
import studio.vibe.shared.model.FilePath
import kotlin.uuid.Uuid

/**
 * Test-only convenience overloads that bind a [DesktopServiceContainer] to the
 * production Composables.  Keeps the production signatures DI-pure (no full
 * container) while letting existing UI tests continue to use the container they
 * already construct via `createIsolatedContainer()`.
 */

@Composable
fun WelcomeView(
    onOpenProject: () -> Unit,
    onCreateNew: () -> Unit = {},
    container: DesktopServiceContainer? = null,
    modifier: Modifier = Modifier,
) {
    WelcomeView(
        onOpenProject = onOpenProject,
        onCreateNew = onCreateNew,
        projectStore = container?.projectStore,
        modifier = modifier,
    )
}

@Composable
fun ToolbarView(
    container: DesktopServiceContainer,
    isCodeSpeakMode: Boolean = false,
    showingChangesPanel: Boolean = false,
    onOpenSettings: () -> Unit = {},
    onToggleCodeSpeakMode: () -> Unit = {},
    onToggleChangesPanel: () -> Unit = {},
    onInstallAgent: (AIAgent) -> Unit = {},
) {
    ToolbarView(
        toolbarViewModel = container.toolbarViewModel,
        remoteControlServer = container.remoteControlServer,
        projectStore = container.projectStore,
        navigationCoordinator = container.navigationCoordinator,
        isCodeSpeakMode = isCodeSpeakMode,
        showingChangesPanel = showingChangesPanel,
        onOpenSettings = onOpenSettings,
        onToggleCodeSpeakMode = onToggleCodeSpeakMode,
        onToggleChangesPanel = onToggleChangesPanel,
        onInstallAgent = onInstallAgent,
    )
}

@Composable
fun TabBarView(
    container: DesktopServiceContainer,
    onOpenProject: () -> Unit,
) {
    TabBarView(
        projectStore = container.projectStore,
        terminalService = container.terminalService,
        toolbarViewModel = container.toolbarViewModel,
        onOpenProject = onOpenProject,
    )
}

@Composable
fun SidebarView(
    container: DesktopServiceContainer,
    onToggleGitPanel: () -> Unit,
    onOpenProject: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    SidebarView(
        projectStore = container.projectStore,
        fileTreeBuilder = container.fileTreeBuilder,
        gitSidebarViewModel = container.gitSidebarViewModel,
        gitService = container.gitService,
        coroutineScope = container.scope,
        onToggleGitPanel = onToggleGitPanel,
        onOpenProject = onOpenProject,
        modifier = modifier,
    )
}

@Composable
fun TerminalAreaView(
    container: DesktopServiceContainer,
    modifier: Modifier = Modifier,
) {
    TerminalAreaView(
        projectStore = container.projectStore,
        terminalService = container.terminalService,
        generalPreferences = container.generalPreferences,
        modifier = modifier,
    )
}

@Composable
fun GitPanel(
    container: DesktopServiceContainer,
    modifier: Modifier = Modifier,
) {
    GitPanel(
        gitSidebarViewModel = container.gitSidebarViewModel,
        projectStore = container.projectStore,
        gitService = container.gitService,
        coroutineScope = container.scope,
        modifier = modifier,
    )
}

@Composable
fun CommitPanel(
    container: DesktopServiceContainer,
    projectId: Uuid,
    projectPath: FilePath,
    modifier: Modifier = Modifier,
) {
    CommitPanel(
        gitSidebarViewModel = container.gitSidebarViewModel,
        projectId = projectId,
        projectPath = projectPath,
        modifier = modifier,
    )
}

@Composable
fun TraceabilityPanelView(
    container: DesktopServiceContainer,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TraceabilityPanelView(
        persistenceStore = container.persistenceStore,
        coroutineScope = container.scope,
        projectStore = container.projectStore,
        onClose = onClose,
        modifier = modifier,
    )
}

@Composable
fun AddProjectPopover(
    container: DesktopServiceContainer,
    onOpenFolder: () -> Unit,
    onCreateNew: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    AddProjectPopover(
        projectStore = container.projectStore,
        onOpenFolder = onOpenFolder,
        onCreateNew = onCreateNew,
        onDismiss = onDismiss,
    )
}

@Composable
fun CodeSpeakModeView(
    container: DesktopServiceContainer,
    modifier: Modifier = Modifier,
) {
    CodeSpeakModeView(
        persistenceStore = container.persistenceStore,
        projectStore = container.projectStore,
        processRunner = container.processRunner,
        coroutineScope = container.scope,
        modifier = modifier,
    )
}

@Composable
fun RootView(
    container: DesktopServiceContainer,
    onOpenProject: () -> Unit,
    showGitPanel: Boolean,
    showSidebar: Boolean = true,
    onToggleGitPanel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RootView(
        projectStore = container.projectStore,
        fileTreeBuilder = container.fileTreeBuilder,
        fileTreeViewModel = container.fileTreeViewModel,
        gitStatusPoller = container.gitStatusPoller,
        gitSidebarViewModel = container.gitSidebarViewModel,
        gitService = container.gitService,
        toolbarViewModel = container.toolbarViewModel,
        terminalService = container.terminalService,
        generalPreferences = container.generalPreferences,
        coroutineScope = container.scope,
        onOpenProject = onOpenProject,
        showGitPanel = showGitPanel,
        showSidebar = showSidebar,
        onToggleGitPanel = onToggleGitPanel,
        modifier = modifier,
    )
}

@Composable
fun SettingsView(
    container: DesktopServiceContainer,
    onDismiss: () -> Unit,
) {
    SettingsView(
        generalPreferences = container.generalPreferences,
        codeSpeakPreferences = container.codeSpeakPreferences,
        remoteControlPreferences = container.remoteControlPreferences,
        remoteControlServer = container.remoteControlServer,
        agentRegistry = container.agentRegistry,
        onDismiss = onDismiss,
    )
}

@Composable
fun GeneralSettingsPane(
    container: DesktopServiceContainer,
    modifier: Modifier = Modifier,
) {
    GeneralSettingsPane(preferences = container.generalPreferences, modifier = modifier)
}

@Composable
fun ClaudeSettingsPane(
    container: DesktopServiceContainer,
    modifier: Modifier = Modifier,
) {
    ClaudeSettingsPane(preferences = container.generalPreferences, modifier = modifier)
}

@Composable
fun CodeSpeakSettingsPane(
    container: DesktopServiceContainer,
    modifier: Modifier = Modifier,
) {
    CodeSpeakSettingsPane(preferences = container.codeSpeakPreferences, modifier = modifier)
}

@Composable
fun RemoteControlSettingsPane(
    container: DesktopServiceContainer,
    modifier: Modifier = Modifier,
) {
    RemoteControlSettingsPane(
        preferences = container.remoteControlPreferences,
        server = container.remoteControlServer,
        modifier = modifier,
    )
}

@Composable
fun LlmSettingsPane(
    agent: AIAgent,
    container: DesktopServiceContainer,
    modifier: Modifier = Modifier,
) {
    LlmSettingsPane(
        agent = agent,
        generalPreferences = container.generalPreferences,
        codeSpeakPreferences = container.codeSpeakPreferences,
        modifier = modifier,
    )
}

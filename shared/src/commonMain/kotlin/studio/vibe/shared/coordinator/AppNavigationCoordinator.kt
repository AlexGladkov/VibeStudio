package studio.vibe.shared.coordinator

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import studio.vibe.shared.contract.AIAgent
import studio.vibe.shared.model.CodeSpeakRunBarState

/**
 * Application mode — determines which UI layout is active.
 */
enum class AppMode {
    Regular,
    CodeSpeak,
}

/**
 * Coordinates global navigation actions (install wizard, settings, panel visibility)
 * between the toolbar and root view layers.
 *
 * Replaces untyped NotificationCenter posts with typed StateFlow properties that
 * any component can observe independently.
 */
class AppNavigationCoordinator {

    // MARK: - Agent Install Wizard

    /** The agent whose install wizard should be shown. `null` = dismissed. */
    private val _agentToInstall = MutableStateFlow<AIAgent?>(null)
    val agentToInstall: StateFlow<AIAgent?> = _agentToInstall.asStateFlow()

    fun setAgentToInstall(agent: AIAgent?) {
        _agentToInstall.value = agent
    }

    // MARK: - App Mode

    private val _currentMode = MutableStateFlow(AppMode.Regular)
    val currentMode: StateFlow<AppMode> = _currentMode.asStateFlow()

    // MARK: - CodeSpeak Build

    private val _codeSpeakBuildRequested = MutableStateFlow(false)
    val codeSpeakBuildRequested: StateFlow<Boolean> = _codeSpeakBuildRequested.asStateFlow()

    fun setCodeSpeakBuildRequested(requested: Boolean) {
        _codeSpeakBuildRequested.value = requested
    }

    // MARK: - CodeSpeak Run Bar State

    private val _runBar = MutableStateFlow(CodeSpeakRunBarState())
    val runBar: StateFlow<CodeSpeakRunBarState> = _runBar.asStateFlow()

    fun updateRunBar(mutate: (CodeSpeakRunBarState) -> CodeSpeakRunBarState) {
        _runBar.update(mutate)
    }

    // MARK: - Settings

    private val _showingSettings = MutableStateFlow(false)
    val showingSettings: StateFlow<Boolean> = _showingSettings.asStateFlow()

    fun setShowingSettings(showing: Boolean) {
        _showingSettings.value = showing
    }

    // MARK: - Panels

    private val _showingChangesPanel = MutableStateFlow(false)
    val showingChangesPanel: StateFlow<Boolean> = _showingChangesPanel.asStateFlow()

    fun setShowingChangesPanel(showing: Boolean) {
        _showingChangesPanel.value = showing
    }

    private val _showingSpecPanel = MutableStateFlow(false)
    val showingSpecPanel: StateFlow<Boolean> = _showingSpecPanel.asStateFlow()

    fun setShowingSpecPanel(showing: Boolean) {
        _showingSpecPanel.value = showing
    }

    private val _showingTraceabilityPanel = MutableStateFlow(false)
    val showingTraceabilityPanel: StateFlow<Boolean> = _showingTraceabilityPanel.asStateFlow()

    fun setShowingTraceabilityPanel(showing: Boolean) {
        _showingTraceabilityPanel.value = showing
    }

    // MARK: - Sidebar

    private val _showingSidebar = MutableStateFlow(true)
    val showingSidebar: StateFlow<Boolean> = _showingSidebar.asStateFlow()

    fun setShowingSidebar(showing: Boolean) {
        _showingSidebar.value = showing
    }

    fun toggleSidebar() {
        _showingSidebar.update { !it }
    }

    // MARK: - CodeSpeak Column Widths (toolbar three-section layout)

    /**
     * Width of the specs-list column in CodeSpeak mode, in pixels (not dp).
     * Updated by [SpecsListColumn] via GeometryReader equivalent (onGloballyPositioned).
     * Read by ToolbarView Box1 to align the breadcrumb to the centre column.
     */
    private val _specsColumnWidth = MutableStateFlow(220f)
    val specsColumnWidth: StateFlow<Float> = _specsColumnWidth.asStateFlow()

    fun setSpecsColumnWidth(widthPx: Float) {
        if (widthPx > 0f) _specsColumnWidth.value = widthPx
    }

    // MARK: - Mode Sync

    /**
     * Update [currentMode] based on whether the active project is a CodeSpeak project.
     *
     * Called by the app lifecycle coordinator whenever the active project changes.
     * The mode is determined solely by the presence of `codespeak.json` at the
     * project root — no user toggle. Switching away from CodeSpeak resets all panels.
     */
    fun syncMode(isCodeSpeak: Boolean) {
        val newMode = if (isCodeSpeak) AppMode.CodeSpeak else AppMode.Regular
        if (newMode == _currentMode.value) return
        _currentMode.value = newMode
        if (newMode == AppMode.CodeSpeak) {
            _showingChangesPanel.value = false
            _showingSpecPanel.value = false
            _showingTraceabilityPanel.value = false
        }
    }
}

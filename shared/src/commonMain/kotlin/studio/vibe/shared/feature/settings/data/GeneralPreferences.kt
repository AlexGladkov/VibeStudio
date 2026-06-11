package studio.vibe.shared.feature.settings.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import studio.vibe.shared.core.common.SettingsStorage
import studio.vibe.shared.feature.settings.domain.model.GeneralPreferencesWriting

/**
 * Application theme selection.
 *
 * [SYSTEM] follows the OS-level dark/light mode preference.
 * [DARK] forces the dark palette regardless of OS setting.
 * [LIGHT] forces the light palette regardless of OS setting.
 */
public enum class AppTheme {
    LIGHT,
    DARK,
    SYSTEM,
}

/**
 * Key-value backed general application preferences.
 *
 * Each field exposes a hot [StateFlow] that emits on every mutation so any
 * [collectAsState] consumer rebuilds when the value changes — including UI
 * surfaces that did not originate the mutation. The convenience `var`
 * accessors remain for ergonomic call-sites.
 *
 * Keys are prefixed with `vs_` to avoid collisions with other services.
 * Backed by [SettingsStorage] for cross-platform persistence (UserDefaults on macOS,
 * java.util.prefs on JVM, etc.).
 */
public class GeneralPreferences(private val storage: SettingsStorage) : GeneralPreferencesWriting {

    private object Keys {
        const val CONFIRM_TAB_CLOSE = "vs_confirm_tab_close"
        const val TERMINAL_FONT_SIZE = "vs_terminal_font_size"
        const val CLAUDE_SKIP_PERMISSIONS = "vs_claude_skip_permissions"
        const val APP_THEME = "vs_app_theme"
    }

    // ── confirmTabClose ──────────────────────────────────────────────────────

    private val _confirmTabClose = MutableStateFlow(loadConfirmTabClose())
    public override val confirmTabCloseFlow: StateFlow<Boolean> = _confirmTabClose.asStateFlow()

    /** Show a confirmation alert before closing a tab. Default: `true`. */
    public override var confirmTabClose: Boolean
        get() = _confirmTabClose.value
        set(value) {
            storage.setBool(Keys.CONFIRM_TAB_CLOSE, value)
            _confirmTabClose.value = value
        }

    // ── claudeSkipPermissions ────────────────────────────────────────────────

    private val _claudeSkipPermissions = MutableStateFlow(storage.getBool(Keys.CLAUDE_SKIP_PERMISSIONS))
    public override val claudeSkipPermissionsFlow: StateFlow<Boolean> = _claudeSkipPermissions.asStateFlow()

    /** Launch Claude with `--dangerously-skip-permissions`. Default: `false`. */
    public override var claudeSkipPermissions: Boolean
        get() = _claudeSkipPermissions.value
        set(value) {
            storage.setBool(Keys.CLAUDE_SKIP_PERMISSIONS, value)
            _claudeSkipPermissions.value = value
        }

    // ── terminalFontSize ─────────────────────────────────────────────────────

    private val _terminalFontSize = MutableStateFlow(storage.getInt(Keys.TERMINAL_FONT_SIZE) ?: 13)
    public override val terminalFontSizeFlow: StateFlow<Int> = _terminalFontSize.asStateFlow()

    /** Terminal font size in points. Default: `13`. Clamped to 9..24. */
    public override var terminalFontSize: Int
        get() = _terminalFontSize.value
        set(value) {
            val clamped = value.coerceIn(9, 24)
            storage.setInt(Keys.TERMINAL_FONT_SIZE, clamped)
            _terminalFontSize.value = clamped
        }

    // ── theme ────────────────────────────────────────────────────────────────

    private val _theme = MutableStateFlow(loadTheme())
    public override val themeFlow: StateFlow<AppTheme> = _theme.asStateFlow()

    /** Application color theme. Default: [AppTheme.DARK]. Stored as enum name. */
    public override var theme: AppTheme
        get() = _theme.value
        set(value) {
            storage.setString(Keys.APP_THEME, value.name)
            _theme.value = value
        }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun loadConfirmTabClose(): Boolean =
        storage.getString(Keys.CONFIRM_TAB_CLOSE)?.toBooleanStrictOrNull() ?: true

    private fun loadTheme(): AppTheme =
        storage.getString(Keys.APP_THEME)
            ?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() }
            ?: AppTheme.DARK
}

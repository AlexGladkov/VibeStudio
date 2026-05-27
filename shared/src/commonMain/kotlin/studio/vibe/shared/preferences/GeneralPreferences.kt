package studio.vibe.shared.preferences

import studio.vibe.shared.contract.SettingsStorage

/**
 * Key-value backed general application preferences.
 *
 * Keys are prefixed with `vs_` to avoid collisions with other services.
 * Backed by [SettingsStorage] for cross-platform persistence (UserDefaults on macOS,
 * SharedPreferences on Android, etc.).
 */
class GeneralPreferences(private val storage: SettingsStorage) {

    private object Keys {
        const val CONFIRM_TAB_CLOSE = "vs_confirm_tab_close"
        const val TERMINAL_FONT_SIZE = "vs_terminal_font_size"
        const val CLAUDE_SKIP_PERMISSIONS = "vs_claude_skip_permissions"
    }

    /**
     * Show a confirmation alert before closing a tab. Default: `true`.
     */
    var confirmTabClose: Boolean
        get() = storage.getString(Keys.CONFIRM_TAB_CLOSE)?.toBooleanStrictOrNull() ?: true
        set(value) { storage.setBool(Keys.CONFIRM_TAB_CLOSE, value) }

    /**
     * Launch Claude with `--dangerously-skip-permissions`. Default: `false`.
     */
    var claudeSkipPermissions: Boolean
        get() = storage.getBool(Keys.CLAUDE_SKIP_PERMISSIONS)
        set(value) { storage.setBool(Keys.CLAUDE_SKIP_PERMISSIONS, value) }

    /**
     * Terminal font size in points. Default: `13`. Range: 9..24.
     */
    var terminalFontSize: Int
        get() = storage.getInt(Keys.TERMINAL_FONT_SIZE) ?: 13
        set(value) {
            val clamped = value.coerceIn(9, 24)
            storage.setInt(Keys.TERMINAL_FONT_SIZE, clamped)
        }
}

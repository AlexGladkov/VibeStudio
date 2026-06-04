package studio.vibe.shared.preferences

import kotlinx.coroutines.flow.StateFlow

/**
 * Abstracts general application preferences so consumers (ViewModels, services)
 * can be tested without a real [SettingsStorage] backend.
 */
interface GeneralPreferencesReading {
    val confirmTabCloseFlow: StateFlow<Boolean>
    val confirmTabClose: Boolean

    val claudeSkipPermissionsFlow: StateFlow<Boolean>
    val claudeSkipPermissions: Boolean

    val terminalFontSizeFlow: StateFlow<Int>
    val terminalFontSize: Int

    val themeFlow: StateFlow<AppTheme>
    val theme: AppTheme
}

/**
 * Extends [GeneralPreferencesReading] with mutation capability.
 *
 * Split by ISP: services that only read preferences depend on
 * [GeneralPreferencesReading]; settings UI depends on [GeneralPreferencesWriting].
 */
interface GeneralPreferencesWriting : GeneralPreferencesReading {
    override var confirmTabClose: Boolean
    override var claudeSkipPermissions: Boolean
    override var terminalFontSize: Int
    override var theme: AppTheme
}

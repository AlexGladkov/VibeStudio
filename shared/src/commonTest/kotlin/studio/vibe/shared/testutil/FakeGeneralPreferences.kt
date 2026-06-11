package studio.vibe.shared.testutil

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import studio.vibe.shared.feature.settings.data.AppTheme
import studio.vibe.shared.feature.settings.domain.model.GeneralPreferencesWriting

class FakeGeneralPreferences(
    confirmTabClose: Boolean = true,
    claudeSkipPermissions: Boolean = false,
    terminalFontSize: Int = 13,
    theme: AppTheme = AppTheme.DARK,
) : GeneralPreferencesWriting {

    private val _confirmTabClose = MutableStateFlow(confirmTabClose)
    override val confirmTabCloseFlow: StateFlow<Boolean> = _confirmTabClose.asStateFlow()
    override var confirmTabClose: Boolean
        get() = _confirmTabClose.value
        set(value) { _confirmTabClose.value = value }

    private val _claudeSkipPermissions = MutableStateFlow(claudeSkipPermissions)
    override val claudeSkipPermissionsFlow: StateFlow<Boolean> = _claudeSkipPermissions.asStateFlow()
    override var claudeSkipPermissions: Boolean
        get() = _claudeSkipPermissions.value
        set(value) { _claudeSkipPermissions.value = value }

    private val _terminalFontSize = MutableStateFlow(terminalFontSize)
    override val terminalFontSizeFlow: StateFlow<Int> = _terminalFontSize.asStateFlow()
    override var terminalFontSize: Int
        get() = _terminalFontSize.value
        set(value) { _terminalFontSize.value = value.coerceIn(9, 24) }

    private val _theme = MutableStateFlow(theme)
    override val themeFlow: StateFlow<AppTheme> = _theme.asStateFlow()
    override var theme: AppTheme
        get() = _theme.value
        set(value) { _theme.value = value }
}

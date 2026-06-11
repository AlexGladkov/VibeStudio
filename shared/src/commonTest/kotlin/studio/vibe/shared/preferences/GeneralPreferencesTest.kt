package studio.vibe.shared.core.common

import studio.vibe.shared.feature.settings.data.AppTheme
import studio.vibe.shared.feature.settings.data.GeneralPreferences
import studio.vibe.shared.testutil.InMemorySettingsStorage
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies reactive StateFlow surface of [GeneralPreferences].
 *
 * Without StateFlow, settings UI cannot react when prefs change in code paths
 * other than the one editing them. Theme + font size + behaviour toggles must
 * all expose hot StateFlows so consumers can [collectAsState] them.
 */
class GeneralPreferencesTest {

    private lateinit var storage: InMemorySettingsStorage
    private lateinit var prefs: GeneralPreferences

    @BeforeTest
    fun setup() {
        storage = InMemorySettingsStorage()
        prefs = GeneralPreferences(storage)
    }

    // ── terminalFontSize ──────────────────────────────────────────────────

    @Test
    fun terminalFontSizeFlow_emitsDefault13_onFreshStorage() {
        assertEquals(13, prefs.terminalFontSizeFlow.value)
    }

    @Test
    fun terminalFontSizeFlow_emitsNewValue_whenMutated() {
        prefs.terminalFontSize = 16
        assertEquals(16, prefs.terminalFontSizeFlow.value)
    }

    @Test
    fun terminalFontSize_isClampedTo9to24Range() {
        prefs.terminalFontSize = 50
        assertEquals(24, prefs.terminalFontSizeFlow.value)
        prefs.terminalFontSize = 3
        assertEquals(9, prefs.terminalFontSizeFlow.value)
    }

    @Test
    fun terminalFontSizeFlow_reflectsStoredValue_onFreshInstance() {
        prefs.terminalFontSize = 18
        val prefs2 = GeneralPreferences(storage)
        assertEquals(18, prefs2.terminalFontSizeFlow.value)
    }

    // ── theme ─────────────────────────────────────────────────────────────

    @Test
    fun themeFlow_emitsDARK_onFreshStorage() {
        assertEquals(AppTheme.DARK, prefs.themeFlow.value)
    }

    @Test
    fun themeFlow_emitsNewValue_whenMutated() {
        prefs.theme = AppTheme.LIGHT
        assertEquals(AppTheme.LIGHT, prefs.themeFlow.value)
        prefs.theme = AppTheme.SYSTEM
        assertEquals(AppTheme.SYSTEM, prefs.themeFlow.value)
    }

    @Test
    fun themeFlow_reflectsStoredValue_onFreshInstance() {
        prefs.theme = AppTheme.LIGHT
        val prefs2 = GeneralPreferences(storage)
        assertEquals(AppTheme.LIGHT, prefs2.themeFlow.value)
    }

    // ── confirmTabClose ───────────────────────────────────────────────────

    @Test
    fun confirmTabCloseFlow_emitsTrue_onFreshStorage() {
        assertEquals(true, prefs.confirmTabCloseFlow.value)
    }

    @Test
    fun confirmTabCloseFlow_emitsNewValue_whenMutated() {
        prefs.confirmTabClose = false
        assertEquals(false, prefs.confirmTabCloseFlow.value)
        prefs.confirmTabClose = true
        assertEquals(true, prefs.confirmTabCloseFlow.value)
    }

    // ── claudeSkipPermissions ─────────────────────────────────────────────

    @Test
    fun claudeSkipPermissionsFlow_emitsFalse_onFreshStorage() {
        assertEquals(false, prefs.claudeSkipPermissionsFlow.value)
    }

    @Test
    fun claudeSkipPermissionsFlow_emitsNewValue_whenMutated() {
        prefs.claudeSkipPermissions = true
        assertEquals(true, prefs.claudeSkipPermissionsFlow.value)
    }

    // ── P1: boundary values ───────────────────────────────────────────────────

    @Test
    fun terminalFontSize_boundary9_staysAt9() {
        // Exact lower boundary must not be clamped further down
        prefs.terminalFontSize = 9
        assertEquals(9, prefs.terminalFontSizeFlow.value)
    }

    @Test
    fun terminalFontSize_boundary24_staysAt24() {
        // Exact upper boundary must not be clamped further up
        prefs.terminalFontSize = 24
        assertEquals(24, prefs.terminalFontSizeFlow.value)
    }

    // ── P1: unknown AppTheme ordinal falls back to DARK ───────────────────────

    @Test
    fun themeFlow_unknownRawValue_fallsBackToDARK() {
        // Arrange — write a raw value that does not match any AppTheme name
        storage.setString("vs_app_theme", "UNKNOWN_THEME_VALUE")

        // Act — create a fresh instance that reads the corrupted storage
        val freshPrefs = GeneralPreferences(storage)

        // Assert — unknown name must fall back to the default DARK theme
        assertEquals(AppTheme.DARK, freshPrefs.themeFlow.value)
    }
}

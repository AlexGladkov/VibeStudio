@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui.screen

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.createIsolatedContainer
import studio.vibe.desktop.ui.VibeStudioDesktopApp
import studio.vibe.desktop.ui.theme.VibeStudioTheme
import studio.vibe.shared.model.SettingsSectionGroup
import java.io.File

/**
 * Integration tests for settings model and the settings overlay smoke test.
 */
class SettingsViewTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var container: DesktopServiceContainer
    private lateinit var tempHome: File

    @Before
    fun setup() {
        val (c, h) = createIsolatedContainer()
        container = c
        tempHome = h
    }

    @After
    fun teardown() {
        container.dispose()
        tempHome.deleteRecursively()
    }

    // ── Model tests (pure logic, no Compose needed) ────────────────────────────

    @Test
    fun settingsModel_generalGroupDisplayName_isCorrect() {
        kotlin.test.assertEquals("General", SettingsSectionGroup.GENERAL.displayName)
    }

    @Test
    fun settingsModel_llmGroupDisplayName_isCorrect() {
        kotlin.test.assertEquals("LLM", SettingsSectionGroup.LLM.displayName)
    }

    @Test
    fun settingsModel_appearanceItem_existsInGeneralGroup() {
        val item = SettingsSectionGroup.GENERAL.items.firstOrNull { it.displayName == "Appearance" }
        kotlin.test.assertNotNull(item)
    }

    @Test
    fun settingsModel_remoteControlItem_existsInGeneralGroup() {
        val item = SettingsSectionGroup.GENERAL.items.firstOrNull { it.displayName == "Remote Control" }
        kotlin.test.assertNotNull(item)
    }

    @Test
    fun settingsModel_llmGroupHasAgentItems() {
        kotlin.test.assertTrue(SettingsSectionGroup.LLM.items.isNotEmpty())
    }

    @Test
    fun settingsModel_allItemsHaveUniqueIds() {
        val allItems = SettingsSectionGroup.entries.flatMap { it.items }
        val ids = allItems.map { it.id }
        kotlin.test.assertEquals(ids.size, ids.toSet().size)
    }

    // ── Compose smoke: settings overlay does not crash ────────────────────────

    @Test
    fun app_settingsOverlay_doesNotCrash_whenShowSettingsTrue() {
        composeTestRule.setContent {
            VibeStudioTheme {
                VibeStudioDesktopApp(
                    container = container,
                    showSettings = true,
                    onToggleSettings = {},
                )
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun app_settingsOverlay_doesNotCrash_whenShowSettingsFalse() {
        composeTestRule.setContent {
            VibeStudioTheme {
                VibeStudioDesktopApp(
                    container = container,
                    showSettings = false,
                    onToggleSettings = {},
                )
            }
        }

        composeTestRule.waitForIdle()
    }
}

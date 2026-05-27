package studio.vibe.desktop.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.createIsolatedContainer
import studio.vibe.desktop.ui.theme.VibeStudioTheme
import java.io.File

/**
 * UI tests for [GeneralSettingsPane].
 *
 * Verifies render stability and presence of key UI elements.
 */
class GeneralSettingsPaneTest {

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

    @Test
    fun generalSettingsPane_rendersWithoutCrash() {
        composeTestRule.setContent {
            VibeStudioTheme {
                GeneralSettingsPane(container = container)
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun generalSettingsPane_showsPaneTitle() {
        composeTestRule.setContent {
            VibeStudioTheme {
                GeneralSettingsPane(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("General").assertIsDisplayed()
    }

    @Test
    fun generalSettingsPane_showsTerminalSectionHeader() {
        composeTestRule.setContent {
            VibeStudioTheme {
                GeneralSettingsPane(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Terminal").assertIsDisplayed()
    }

    @Test
    fun generalSettingsPane_showsFontSizeSetting() {
        composeTestRule.setContent {
            VibeStudioTheme {
                GeneralSettingsPane(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Font size").assertIsDisplayed()
    }

    @Test
    fun generalSettingsPane_showsBehaviourSection() {
        composeTestRule.setContent {
            VibeStudioTheme {
                GeneralSettingsPane(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Behaviour").assertIsDisplayed()
    }

    @Test
    fun generalSettingsPane_showsConfirmTabCloseToggle() {
        composeTestRule.setContent {
            VibeStudioTheme {
                GeneralSettingsPane(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Confirm tab close").assertIsDisplayed()
    }

    @Test
    fun generalSettingsPane_showsSkipPermissionsToggle() {
        composeTestRule.setContent {
            VibeStudioTheme {
                GeneralSettingsPane(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("--dangerously-skip-permissions").assertIsDisplayed()
    }
}

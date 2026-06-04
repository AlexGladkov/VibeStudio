package studio.vibe.desktop.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
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
 * UI tests for [OpencodeSettingsPane].
 */
class OpencodeSettingsPaneTest {

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
    fun opencodeSettingsPane_rendersWithoutCrash() {
        composeTestRule.setContent {
            VibeStudioTheme {
                OpencodeSettingsPane()
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun opencodeSettingsPane_showsPaneTitle() {
        composeTestRule.setContent {
            VibeStudioTheme {
                OpencodeSettingsPane()
            }
        }

        composeTestRule.waitForIdle()
        // OpenCodeAgent.displayName = "opencode"
        composeTestRule.onNodeWithText("opencode").assertIsDisplayed()
    }

    @Test
    fun opencodeSettingsPane_showsConfigDirectorySection() {
        composeTestRule.setContent {
            VibeStudioTheme {
                OpencodeSettingsPane()
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Config directory").assertIsDisplayed()
    }

    @Test
    fun opencodeSettingsPane_showsPluginsSection() {
        composeTestRule.setContent {
            VibeStudioTheme {
                OpencodeSettingsPane()
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Plugins").assertIsDisplayed()
    }

    @Test
    fun opencodeSettingsPane_showsPluginsSection_alwaysPresent() {
        // The "Plugins" section header always renders regardless of whether
        // ~/.config/opencode/plugins/ exists on the host machine.
        composeTestRule.setContent {
            VibeStudioTheme {
                OpencodeSettingsPane()
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Plugins").assertIsDisplayed()
    }

    @Test
    fun opencodeSettingsPane_showsAuthenticationSection() {
        composeTestRule.setContent {
            VibeStudioTheme {
                OpencodeSettingsPane()
            }
        }

        composeTestRule.waitForIdle()
        // OpencodeSettingsPane renders "Authentication" as a section label AND as
        // the LlmNoKeySection title — two nodes exist. Use onAllNodesWithText
        // to confirm at least one is present.
        composeTestRule.onAllNodes(hasText("Authentication"))
            .also { it[0].assertExists() }
    }

    @Test
    fun opencodeSettingsPane_showsProvidersCliHint() {
        composeTestRule.setContent {
            VibeStudioTheme {
                OpencodeSettingsPane()
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(
            "Providers and API keys are managed via CLI: opencode providers",
        ).assertIsDisplayed()
    }

    @Test
    fun opencodeSettingsPane_showsInstallationSection() {
        composeTestRule.setContent {
            VibeStudioTheme {
                OpencodeSettingsPane()
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Installation").assertIsDisplayed()
    }

    @Test
    fun opencodeSettingsPane_showsShortDescription() {
        composeTestRule.setContent {
            VibeStudioTheme {
                OpencodeSettingsPane()
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(
            "Open-source AI coding assistant with support for multiple AI models.",
        ).assertIsDisplayed()
    }
}

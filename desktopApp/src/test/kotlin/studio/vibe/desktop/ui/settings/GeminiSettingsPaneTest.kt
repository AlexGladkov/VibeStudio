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
 * UI tests for [GeminiSettingsPane].
 */
class GeminiSettingsPaneTest {

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
    fun geminiSettingsPane_rendersWithoutCrash() {
        composeTestRule.setContent {
            VibeStudioTheme {
                GeminiSettingsPane(container = container)
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun geminiSettingsPane_showsPaneTitle() {
        composeTestRule.setContent {
            VibeStudioTheme {
                GeminiSettingsPane(container = container)
            }
        }

        composeTestRule.waitForIdle()
        // AIAssistant.GEMINI.displayName = "gemini"
        composeTestRule.onNodeWithText("gemini").assertIsDisplayed()
    }

    @Test
    fun geminiSettingsPane_showsConfigSection() {
        composeTestRule.setContent {
            VibeStudioTheme {
                GeminiSettingsPane(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Config").assertIsDisplayed()
    }

    @Test
    fun geminiSettingsPane_showsApiKeyConfigurationSection() {
        composeTestRule.setContent {
            VibeStudioTheme {
                GeminiSettingsPane(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("API key configuration").assertIsDisplayed()
    }

    @Test
    fun geminiSettingsPane_showsInstallationSection() {
        composeTestRule.setContent {
            VibeStudioTheme {
                GeminiSettingsPane(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Installation").assertIsDisplayed()
    }

    @Test
    fun geminiSettingsPane_showsShortDescription() {
        composeTestRule.setContent {
            VibeStudioTheme {
                GeminiSettingsPane(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(
            "Google Gemini CLI for AI-powered code assistance and generation.",
        ).assertIsDisplayed()
    }

    @Test
    fun geminiSettingsPane_showsConfigFileNotFoundHint_whenFileAbsent() {
        composeTestRule.setContent {
            VibeStudioTheme {
                GeminiSettingsPane(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()

        // In the test environment ~/.gemini/settings.json won't exist
        composeTestRule.onNodeWithText("not found — run `gemini` to auto-create on first launch").assertIsDisplayed()
    }
}

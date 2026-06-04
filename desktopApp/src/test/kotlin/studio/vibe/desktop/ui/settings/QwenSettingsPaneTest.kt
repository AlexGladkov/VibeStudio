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
 * UI tests for [QwenSettingsPane].
 */
class QwenSettingsPaneTest {

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
    fun qwenSettingsPane_rendersWithoutCrash() {
        composeTestRule.setContent {
            VibeStudioTheme {
                QwenSettingsPane()
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun qwenSettingsPane_showsPaneTitle() {
        composeTestRule.setContent {
            VibeStudioTheme {
                QwenSettingsPane()
            }
        }

        composeTestRule.waitForIdle()
        // AIAssistant.QWEN_CODE.displayName = "qwen"
        composeTestRule.onNodeWithText("qwen").assertIsDisplayed()
    }

    @Test
    fun qwenSettingsPane_showsGlobalConfigSection() {
        composeTestRule.setContent {
            VibeStudioTheme {
                QwenSettingsPane()
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Global config").assertIsDisplayed()
    }

    @Test
    fun qwenSettingsPane_showsSubagentsSectionHeader() {
        composeTestRule.setContent {
            VibeStudioTheme {
                QwenSettingsPane()
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Subagents").assertIsDisplayed()
    }

    @Test
    fun qwenSettingsPane_showsEmptyAgentsState_whenDirDoesNotExist() {
        composeTestRule.setContent {
            VibeStudioTheme {
                QwenSettingsPane()
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("No agents in ~/.qwen/agents/").assertIsDisplayed()
    }

    @Test
    fun qwenSettingsPane_showsApiKeyConfigurationSection() {
        composeTestRule.setContent {
            VibeStudioTheme {
                QwenSettingsPane()
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("API key configuration").assertIsDisplayed()
    }

    @Test
    fun qwenSettingsPane_showsInstallationSection() {
        composeTestRule.setContent {
            VibeStudioTheme {
                QwenSettingsPane()
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Installation").assertIsDisplayed()
    }

    @Test
    fun qwenSettingsPane_showsShortDescription() {
        composeTestRule.setContent {
            VibeStudioTheme {
                QwenSettingsPane()
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(
            "Alibaba's Qwen Code CLI for code generation and completion.",
        ).assertIsDisplayed()
    }
}

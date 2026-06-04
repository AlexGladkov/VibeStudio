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
 * UI tests for [CodexSettingsPane].
 */
class CodexSettingsPaneTest {

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
    fun codexSettingsPane_rendersWithoutCrash() {
        composeTestRule.setContent {
            VibeStudioTheme {
                CodexSettingsPane()
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun codexSettingsPane_showsPaneTitle() {
        composeTestRule.setContent {
            VibeStudioTheme {
                CodexSettingsPane()
            }
        }

        composeTestRule.waitForIdle()
        // CodexAgent.displayName = "codex"
        composeTestRule.onNodeWithText("codex").assertIsDisplayed()
    }

    @Test
    fun codexSettingsPane_showsConfigSection() {
        composeTestRule.setContent {
            VibeStudioTheme {
                CodexSettingsPane()
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Config").assertIsDisplayed()
    }

    @Test
    fun codexSettingsPane_showsMemoriesSection() {
        composeTestRule.setContent {
            VibeStudioTheme {
                CodexSettingsPane()
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Memories").assertIsDisplayed()
    }

    @Test
    fun codexSettingsPane_showsSkillsSection() {
        composeTestRule.setContent {
            VibeStudioTheme {
                CodexSettingsPane()
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Skills").assertIsDisplayed()
    }

    @Test
    fun codexSettingsPane_showsEmptyMemoriesState_whenDirDoesNotExist() {
        composeTestRule.setContent {
            VibeStudioTheme {
                CodexSettingsPane()
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("No memory files in ~/.codex/memories/").assertIsDisplayed()
    }

    @Test
    fun codexSettingsPane_showsEmptySkillsState_whenDirDoesNotExist() {
        composeTestRule.setContent {
            VibeStudioTheme {
                CodexSettingsPane()
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("No skills in ~/.codex/skills/").assertIsDisplayed()
    }

    @Test
    fun codexSettingsPane_showsInstallationSection() {
        composeTestRule.setContent {
            VibeStudioTheme {
                CodexSettingsPane()
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Installation").assertIsDisplayed()
    }

    @Test
    fun codexSettingsPane_showsNpmInstallHint() {
        composeTestRule.setContent {
            VibeStudioTheme {
                CodexSettingsPane()
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("npm install -g @openai/codex").assertIsDisplayed()
    }
}

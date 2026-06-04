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
 * UI tests for [ClaudeSettingsPane].
 *
 * ClaudeSettingsPane is rendered INSIDE SettingsView (not a DialogWindow),
 * so it can be tested with [createComposeRule].
 */
class ClaudeSettingsPaneTest {

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
    fun claudeSettingsPane_rendersWithoutCrash() {
        composeTestRule.setContent {
            VibeStudioTheme {
                ClaudeSettingsPane(preferences = container.generalPreferences)
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun claudeSettingsPane_showsCluadeSectionTitle() {
        composeTestRule.setContent {
            VibeStudioTheme {
                ClaudeSettingsPane(preferences = container.generalPreferences)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Claude").assertIsDisplayed()
    }

    @Test
    fun claudeSettingsPane_showsLaunchSectionHeader() {
        composeTestRule.setContent {
            VibeStudioTheme {
                ClaudeSettingsPane(preferences = container.generalPreferences)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Launch").assertIsDisplayed()
    }

    @Test
    fun claudeSettingsPane_showsSkipPermissionsLaunchOption() {
        composeTestRule.setContent {
            VibeStudioTheme {
                ClaudeSettingsPane(preferences = container.generalPreferences)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("--dangerously-skip-permissions").assertIsDisplayed()
    }

    @Test
    fun claudeSettingsPane_showsGlobalConfigSection() {
        composeTestRule.setContent {
            VibeStudioTheme {
                ClaudeSettingsPane(preferences = container.generalPreferences)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Global config").assertIsDisplayed()
    }

    @Test
    fun claudeSettingsPane_showsSubagentsSectionHeader() {
        composeTestRule.setContent {
            VibeStudioTheme {
                ClaudeSettingsPane(preferences = container.generalPreferences)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Subagents").assertIsDisplayed()
    }

    @Test
    fun claudeSettingsPane_showsAgentsSectionHeader() {
        // Tests the Subagents section header, which always renders regardless of
        // whether ~/.claude/agents/ exists or is empty on the host machine.
        composeTestRule.setContent {
            VibeStudioTheme {
                ClaudeSettingsPane(preferences = container.generalPreferences)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Subagents").assertIsDisplayed()
    }

    @Test
    fun claudeSettingsPane_showsInstallationSection() {
        composeTestRule.setContent {
            VibeStudioTheme {
                ClaudeSettingsPane(preferences = container.generalPreferences)
            }
        }

        composeTestRule.waitForIdle()
        // Use assertExists instead of assertIsDisplayed — the node may be
        // outside the headless viewport in a scrollable column
        composeTestRule.onNodeWithText("Installation").assertExists()
    }

    @Test
    fun claudeSettingsPane_showsNpmInstallHint() {
        composeTestRule.setContent {
            VibeStudioTheme {
                ClaudeSettingsPane(preferences = container.generalPreferences)
            }
        }

        composeTestRule.waitForIdle()
        // Use assertExists — node is in the composition tree but may be scrolled off-screen
        composeTestRule.onNodeWithText("npm install -g @anthropic-ai/claude-code").assertExists()
    }
}

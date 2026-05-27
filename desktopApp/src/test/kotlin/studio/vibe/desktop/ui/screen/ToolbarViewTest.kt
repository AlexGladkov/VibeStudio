@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui.screen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.createIsolatedContainer
import studio.vibe.desktop.ui.ToolbarView
import studio.vibe.desktop.ui.theme.VibeStudioTheme
import studio.vibe.shared.model.AIAssistant
import java.io.File

/**
 * Integration tests for [ToolbarView].
 */
class ToolbarViewTest {

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
    fun toolbarView_rendersWithoutCrash() {
        composeTestRule.setContent {
            VibeStudioTheme {
                ToolbarView(
                    container = container,
                    isCodeSpeakMode = false,
                    onOpenSettings = {},
                    onToggleCodeSpeakMode = {},
                    onInstallAgent = {},
                )
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun toolbarView_agentPickerShowsDefaultAgentName() {
        composeTestRule.setContent {
            VibeStudioTheme {
                ToolbarView(
                    container = container,
                    isCodeSpeakMode = false,
                    onOpenSettings = {},
                    onToggleCodeSpeakMode = {},
                    onInstallAgent = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        // Default selected agent per ToolbarViewModel initial state is Claude.
        composeTestRule.onNodeWithText(AIAssistant.CLAUDE.displayName).assertExists()
    }

    @Test
    fun toolbarView_playButtonVisible_whenNotRunning() {
        composeTestRule.setContent {
            VibeStudioTheme {
                ToolbarView(
                    container = container,
                    isCodeSpeakMode = false,
                    onOpenSettings = {},
                    onToggleCodeSpeakMode = {},
                    onInstallAgent = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Launch agent").assertExists()
    }

    @Test
    fun toolbarView_settingsButtonVisible() {
        composeTestRule.setContent {
            VibeStudioTheme {
                ToolbarView(
                    container = container,
                    isCodeSpeakMode = false,
                    onOpenSettings = {},
                    onToggleCodeSpeakMode = {},
                    onInstallAgent = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Settings").assertExists()
    }

    @Test
    fun toolbarView_codeSpeakToggleShowsEnterState_whenModeInactive() {
        composeTestRule.setContent {
            VibeStudioTheme {
                ToolbarView(
                    container = container,
                    isCodeSpeakMode = false,
                    onOpenSettings = {},
                    onToggleCodeSpeakMode = {},
                    onInstallAgent = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Enter CodeSpeak mode").assertExists()
    }

    @Test
    fun toolbarView_codeSpeakToggleShowsExitState_whenModeActive() {
        composeTestRule.setContent {
            VibeStudioTheme {
                ToolbarView(
                    container = container,
                    isCodeSpeakMode = true,
                    onOpenSettings = {},
                    onToggleCodeSpeakMode = {},
                    onInstallAgent = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Exit CodeSpeak mode").assertExists()
    }
}

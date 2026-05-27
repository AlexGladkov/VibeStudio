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
 * UI tests for [CodeSpeakSettingsPane].
 *
 * CodeSpeakSettingsPane is rendered inside SettingsView (not a DialogWindow).
 * The pane contains LaunchedEffect coroutines for binary detection; tests
 * use [mainClock.advanceTimeBy] after [waitForIdle] to let coroutines settle.
 */
class CodeSpeakSettingsPaneTest {

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
    fun codeSpeakSettingsPane_rendersWithoutCrash() {
        composeTestRule.setContent {
            VibeStudioTheme {
                CodeSpeakSettingsPane(container = container)
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun codeSpeakSettingsPane_showsPaneTitle() {
        composeTestRule.setContent {
            VibeStudioTheme {
                CodeSpeakSettingsPane(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("CodeSpeak").assertIsDisplayed()
    }

    @Test
    fun codeSpeakSettingsPane_showsStatusSectionHeader() {
        composeTestRule.setContent {
            VibeStudioTheme {
                CodeSpeakSettingsPane(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Status").assertIsDisplayed()
    }

    @Test
    fun codeSpeakSettingsPane_showsBehaviourSectionHeader() {
        composeTestRule.setContent {
            VibeStudioTheme {
                CodeSpeakSettingsPane(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Behaviour").assertIsDisplayed()
    }

    @Test
    fun codeSpeakSettingsPane_showsAutoBuildOnSaveToggle() {
        composeTestRule.setContent {
            VibeStudioTheme {
                CodeSpeakSettingsPane(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Auto-build on save").assertIsDisplayed()
    }

    @Test
    fun codeSpeakSettingsPane_showsBuildOnProjectOpenToggle() {
        composeTestRule.setContent {
            VibeStudioTheme {
                CodeSpeakSettingsPane(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Build on project open").assertIsDisplayed()
    }

    @Test
    fun codeSpeakSettingsPane_showsAutoOpenBuildPanelToggle() {
        composeTestRule.setContent {
            VibeStudioTheme {
                CodeSpeakSettingsPane(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Auto-open build panel").assertIsDisplayed()
    }

    @Test
    fun codeSpeakSettingsPane_showsDisplaySectionHeader() {
        composeTestRule.setContent {
            VibeStudioTheme {
                CodeSpeakSettingsPane(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Display").assertIsDisplayed()
    }

    @Test
    fun codeSpeakSettingsPane_showsShowFailingOnlyToggle() {
        composeTestRule.setContent {
            VibeStudioTheme {
                CodeSpeakSettingsPane(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Show failing specs only").assertIsDisplayed()
    }

    @Test
    fun codeSpeakSettingsPane_showsNotificationsSectionHeader() {
        composeTestRule.setContent {
            VibeStudioTheme {
                CodeSpeakSettingsPane(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Notifications").assertIsDisplayed()
    }
}

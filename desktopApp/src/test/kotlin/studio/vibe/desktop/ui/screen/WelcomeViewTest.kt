@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui.screen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.createIsolatedContainer
import studio.vibe.desktop.testutil.WelcomeView
import studio.vibe.desktop.ui.theme.VibeStudioTheme
import java.io.File

/**
 * Integration tests for [WelcomeView].
 */
class WelcomeViewTest {

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
    fun welcomeView_rendersWithoutCrash() {
        composeTestRule.setContent {
            VibeStudioTheme {
                WelcomeView(
                    onOpenProject = {},
                    onCreateNew = {},
                    container = container,
                )
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun welcomeView_titleIsDisplayed() {
        composeTestRule.setContent {
            VibeStudioTheme {
                WelcomeView(
                    onOpenProject = {},
                    onCreateNew = {},
                    container = null,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("VibeStudio").assertExists()
    }

    @Test
    fun welcomeView_subtitleIsDisplayed() {
        composeTestRule.setContent {
            VibeStudioTheme {
                WelcomeView(
                    onOpenProject = {},
                    onCreateNew = {},
                    container = null,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Open a folder to get started").assertExists()
    }

    @Test
    fun welcomeView_openFolderButtonIsVisible() {
        composeTestRule.setContent {
            VibeStudioTheme {
                WelcomeView(
                    onOpenProject = {},
                    onCreateNew = {},
                    container = null,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Open Folder").assertExists()
    }

    @Test
    fun welcomeView_openFolderButtonFiresCallback() {
        var fired = false

        composeTestRule.setContent {
            VibeStudioTheme {
                WelcomeView(
                    onOpenProject = { fired = true },
                    onCreateNew = {},
                    container = null,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Open Folder").performClick()
        composeTestRule.waitForIdle()

        kotlin.test.assertTrue(fired)
    }

    @Test
    fun welcomeView_createNewButtonIsVisible() {
        composeTestRule.setContent {
            VibeStudioTheme {
                WelcomeView(
                    onOpenProject = {},
                    onCreateNew = {},
                    container = null,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Create New").assertExists()
    }

    @Test
    fun welcomeView_createNewButtonFiresCallback() {
        var fired = false

        composeTestRule.setContent {
            VibeStudioTheme {
                WelcomeView(
                    onOpenProject = {},
                    onCreateNew = { fired = true },
                    container = null,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Create New").performClick()
        composeTestRule.waitForIdle()

        kotlin.test.assertTrue(fired)
    }

    @Test
    fun welcomeView_recentSectionHidden_whenNoRecentProjects() {
        composeTestRule.setContent {
            VibeStudioTheme {
                WelcomeView(
                    onOpenProject = {},
                    onCreateNew = {},
                    container = null,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("RECENT").assertDoesNotExist()
    }
}

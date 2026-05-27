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
import studio.vibe.desktop.ui.AddProjectPopover
import studio.vibe.desktop.ui.theme.VibeStudioTheme
import java.io.File
import kotlin.test.assertTrue

/**
 * UI tests for [AddProjectPopover].
 */
class AddProjectPopoverTest {

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
    fun addProjectPopover_rendersWithoutCrash() {
        composeTestRule.setContent {
            VibeStudioTheme {
                AddProjectPopover(
                    container = container,
                    onOpenFolder = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun addProjectPopover_showsOpenFolderAction() {
        composeTestRule.setContent {
            VibeStudioTheme {
                AddProjectPopover(
                    container = container,
                    onOpenFolder = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Open Folder...").assertExists()
    }

    @Test
    fun addProjectPopover_showsEmptyState_whenNoRecentProjects() {
        composeTestRule.setContent {
            VibeStudioTheme {
                AddProjectPopover(
                    container = container,
                    onOpenFolder = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("No recent projects").assertExists()
    }

    @Test
    fun addProjectPopover_openFolderButton_invokesCallback() {
        var openFolderCalled = false

        composeTestRule.setContent {
            VibeStudioTheme {
                AddProjectPopover(
                    container = container,
                    onOpenFolder = { openFolderCalled = true },
                    onDismiss = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Open Folder...").performClick()
        composeTestRule.waitForIdle()

        assertTrue(openFolderCalled)
    }

    @Test
    fun addProjectPopover_showsCreateNewAction_whenCallbackProvided() {
        composeTestRule.setContent {
            VibeStudioTheme {
                AddProjectPopover(
                    container = container,
                    onOpenFolder = {},
                    onCreateNew = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Create New...").assertExists()
    }

    @Test
    fun addProjectPopover_doesNotShowCreateNew_whenCallbackIsNull() {
        composeTestRule.setContent {
            VibeStudioTheme {
                AddProjectPopover(
                    container = container,
                    onOpenFolder = {},
                    onCreateNew = null,
                    onDismiss = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Create New...").assertDoesNotExist()
    }
}

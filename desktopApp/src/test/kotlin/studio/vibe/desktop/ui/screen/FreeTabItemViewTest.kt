package studio.vibe.desktop.ui.screen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Rule
import org.junit.Test
import studio.vibe.desktop.ui.FreeTabItemView
import studio.vibe.desktop.ui.theme.VibeStudioTheme

/**
 * UI tests for [FreeTabItemView].
 */
class FreeTabItemViewTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun freeTabItemView_rendersWithoutCrash() {
        composeTestRule.setContent {
            VibeStudioTheme {
                FreeTabItemView(
                    title = "Terminal 1",
                    isActive = false,
                    onClick = {},
                    onClose = {},
                    onRename = {},
                )
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun freeTabItemView_rendersTabTitle() {
        composeTestRule.setContent {
            VibeStudioTheme {
                FreeTabItemView(
                    title = "Terminal 1",
                    isActive = false,
                    onClick = {},
                    onClose = {},
                    onRename = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Terminal 1").assertExists()
    }

    @Test
    fun freeTabItemView_activeTab_showsCloseButton() {
        // The close button is always visible when isActive = true
        composeTestRule.setContent {
            VibeStudioTheme {
                FreeTabItemView(
                    title = "Terminal 1",
                    isActive = true,
                    onClick = {},
                    onClose = {},
                    onRename = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Close tab").assertExists()
    }

    @Test
    fun freeTabItemView_inactiveTab_doesNotShowCloseButton() {
        // Close button is hidden when not active and not hovered
        composeTestRule.setContent {
            VibeStudioTheme {
                FreeTabItemView(
                    title = "Terminal 1",
                    isActive = false,
                    onClick = {},
                    onClose = {},
                    onRename = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Close tab").assertDoesNotExist()
    }

    @Test
    fun freeTabItemView_differentTitles_areRenderedCorrectly() {
        composeTestRule.setContent {
            VibeStudioTheme {
                FreeTabItemView(
                    title = "My Custom Session",
                    isActive = false,
                    onClick = {},
                    onClose = {},
                    onRename = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("My Custom Session").assertExists()
    }
}

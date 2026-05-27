package studio.vibe.desktop.ui.screen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import studio.vibe.desktop.ui.TerminalPlaceholder
import studio.vibe.desktop.ui.theme.VibeStudioTheme

/**
 * UI tests for [TerminalPlaceholder].
 */
class TerminalPlaceholderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun terminalPlaceholder_rendersWithoutCrash() {
        composeTestRule.setContent {
            VibeStudioTheme {
                TerminalPlaceholder()
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun terminalPlaceholder_showsTerminalAreaText() {
        composeTestRule.setContent {
            VibeStudioTheme {
                TerminalPlaceholder()
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Terminal area").assertExists()
    }

    @Test
    fun terminalPlaceholder_showsCursorPrompt() {
        composeTestRule.setContent {
            VibeStudioTheme {
                TerminalPlaceholder()
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("\$ _").assertExists()
    }

    @Test
    fun terminalPlaceholder_showsPendingIntegrationMessage() {
        composeTestRule.setContent {
            VibeStudioTheme {
                TerminalPlaceholder()
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("pty4j integration pending").assertExists()
    }
}

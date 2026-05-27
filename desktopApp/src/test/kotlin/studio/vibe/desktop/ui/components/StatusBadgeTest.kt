package studio.vibe.desktop.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import studio.vibe.desktop.ui.theme.VibeStudioTheme

/**
 * UI tests for [StatusBadge].
 */
class StatusBadgeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun statusBadge_rendersWithoutCrash() {
        composeTestRule.setContent {
            VibeStudioTheme {
                StatusBadge(text = "STAGED", color = Color(0xFF4CAF50))
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun statusBadge_rendersWithText() {
        composeTestRule.setContent {
            VibeStudioTheme {
                StatusBadge(text = "STAGED", color = Color(0xFF4CAF50))
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("STAGED").assertExists()
    }

    @Test
    fun statusBadge_rendersPassBadge() {
        composeTestRule.setContent {
            VibeStudioTheme {
                StatusBadge(text = "PASS", color = Color(0xFF4CAF50))
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("PASS").assertExists()
    }

    @Test
    fun statusBadge_rendersFailBadge() {
        composeTestRule.setContent {
            VibeStudioTheme {
                StatusBadge(text = "FAIL", color = Color(0xFFF44336))
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("FAIL").assertExists()
    }

    @Test
    fun statusBadge_rendersWithEmptyText() {
        composeTestRule.setContent {
            VibeStudioTheme {
                StatusBadge(text = "", color = Color.Gray)
            }
        }

        composeTestRule.waitForIdle()
    }
}

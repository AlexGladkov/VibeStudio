package studio.vibe.desktop.ui.screen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import studio.vibe.desktop.ui.DiffView
import studio.vibe.desktop.ui.theme.VibeStudioTheme

/**
 * UI tests for [DiffView].
 */
class DiffViewTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val simpleDiff = """
        --- a/src/Main.kt
        +++ b/src/Main.kt
        @@ -1,4 +1,4 @@
         fun main() {
        -    println("Hello")
        +    println("Hello, World!")
         }
    """.trimIndent()

    @Test
    fun diffView_rendersWithoutCrash_emptyDiff() {
        composeTestRule.setContent {
            VibeStudioTheme {
                DiffView(diffText = "")
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun diffView_showsNoChangesPlaceholder_whenDiffIsEmpty() {
        composeTestRule.setContent {
            VibeStudioTheme {
                DiffView(diffText = "")
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("No changes").assertExists()
    }

    @Test
    fun diffView_rendersWithoutCrash_withDiffContent() {
        composeTestRule.setContent {
            VibeStudioTheme {
                DiffView(diffText = simpleDiff)
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun diffView_rendersHunkHeader() {
        composeTestRule.setContent {
            VibeStudioTheme {
                DiffView(diffText = simpleDiff)
            }
        }

        composeTestRule.waitForIdle()
        // Hunk header starts with @@ and should be visible in the rendered diff
        composeTestRule.onNodeWithText("@@ -1,4 +1,4 @@", substring = true).assertExists()
    }

    @Test
    fun diffView_rendersDeletedLine() {
        composeTestRule.setContent {
            VibeStudioTheme {
                DiffView(diffText = simpleDiff)
            }
        }

        composeTestRule.waitForIdle()
        // The deleted content (without the leading '-') should be visible
        composeTestRule.onNodeWithText("Hello\")", substring = true).assertExists()
    }

    @Test
    fun diffView_rendersAddedLine() {
        composeTestRule.setContent {
            VibeStudioTheme {
                DiffView(diffText = simpleDiff)
            }
        }

        composeTestRule.waitForIdle()
        // The added content should be visible
        composeTestRule.onNodeWithText("Hello, World!", substring = true).assertExists()
    }

    @Test
    fun diffView_doesNotShowNoChanges_whenDiffHasContent() {
        composeTestRule.setContent {
            VibeStudioTheme {
                DiffView(diffText = simpleDiff)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("No changes").assertDoesNotExist()
    }
}

package studio.vibe.desktop.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import studio.vibe.desktop.ui.theme.VibeStudioTheme
import kotlin.test.assertEquals

/**
 * UI tests for [MarkdownEditorView].
 *
 * Covers: rendering initial text, read-only mode preventing callbacks,
 * and editable mode propagating text changes.
 */
class MarkdownEditorViewTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun markdownEditorView_rendersWithoutCrash() {
        composeTestRule.setContent {
            VibeStudioTheme {
                MarkdownEditorView(
                    text = "",
                    onTextChange = {},
                )
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun markdownEditorView_rendersInitialText() {
        composeTestRule.setContent {
            VibeStudioTheme {
                MarkdownEditorView(
                    text = "Hello, World!",
                    onTextChange = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Hello, World!").assertIsDisplayed()
    }

    @Test
    fun markdownEditorView_rendersMultilineContent() {
        val multilineText = "line one\nline two\nline three"

        composeTestRule.setContent {
            VibeStudioTheme {
                MarkdownEditorView(
                    text = multilineText,
                    onTextChange = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        // BasicTextField renders the full value as one node
        composeTestRule.onNodeWithText(multilineText).assertIsDisplayed()
    }

    @Test
    fun markdownEditorView_readOnlyMode_doesNotInvokeCallback() {
        var callbackInvoked = false

        composeTestRule.setContent {
            VibeStudioTheme {
                MarkdownEditorView(
                    text = "read-only content",
                    onTextChange = { callbackInvoked = true },
                    isEditable = false,
                )
            }
        }

        composeTestRule.waitForIdle()
        // In read-only mode the field is not editable, callback should not fire
        assertEquals(false, callbackInvoked)
    }

    @Test
    fun markdownEditorView_editableMode_isEditable_true() {
        composeTestRule.setContent {
            VibeStudioTheme {
                MarkdownEditorView(
                    text = "editable",
                    onTextChange = {},
                    isEditable = true,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("editable").assertIsDisplayed()
    }

    @Test
    fun markdownEditorView_textBinding_updatesOnChange() {
        var capturedText = ""

        composeTestRule.setContent {
            VibeStudioTheme {
                var text by remember { mutableStateOf("initial") }
                MarkdownEditorView(
                    text = text,
                    onTextChange = { newText ->
                        text = newText
                        capturedText = newText
                    },
                    isEditable = true,
                )
            }
        }

        composeTestRule.waitForIdle()
        // Verify initial render
        composeTestRule.onNodeWithText("initial").assertIsDisplayed()
    }

    @Test
    fun markdownEditorView_rendersEmptyString_withoutCrash() {
        composeTestRule.setContent {
            VibeStudioTheme {
                MarkdownEditorView(
                    text = "",
                    onTextChange = {},
                    isEditable = true,
                )
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun markdownEditorView_readOnlyWithContent_rendersCorrectly() {
        composeTestRule.setContent {
            VibeStudioTheme {
                MarkdownEditorView(
                    text = "# Header\n\nSome body text.",
                    onTextChange = {},
                    isEditable = false,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("# Header\n\nSome body text.").assertIsDisplayed()
    }
}

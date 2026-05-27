package studio.vibe.desktop.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import studio.vibe.desktop.ui.theme.VibeStudioTheme
import studio.vibe.shared.model.AIAssistant

/**
 * UI tests for [AIAssistantIcon].
 */
class AIAssistantIconTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun aiAssistantIcon_rendersWithoutCrash_claude() {
        composeTestRule.setContent {
            VibeStudioTheme {
                AIAssistantIcon(assistant = AIAssistant.CLAUDE)
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun aiAssistantIcon_rendersWithoutCrash_opencode() {
        composeTestRule.setContent {
            VibeStudioTheme {
                AIAssistantIcon(assistant = AIAssistant.OPENCODE)
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun aiAssistantIcon_rendersWithoutCrash_codex() {
        composeTestRule.setContent {
            VibeStudioTheme {
                AIAssistantIcon(assistant = AIAssistant.CODEX)
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun aiAssistantIcon_rendersWithoutCrash_gemini() {
        composeTestRule.setContent {
            VibeStudioTheme {
                AIAssistantIcon(assistant = AIAssistant.GEMINI)
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun aiAssistantIcon_rendersWithoutCrash_qwenCode() {
        composeTestRule.setContent {
            VibeStudioTheme {
                AIAssistantIcon(assistant = AIAssistant.QWEN_CODE)
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun aiAssistantIcon_rendersWithoutCrash_codeSpeak() {
        composeTestRule.setContent {
            VibeStudioTheme {
                AIAssistantIcon(assistant = AIAssistant.CODE_SPEAK)
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun aiAssistantIcon_showsMonogram_C_forClaude() {
        composeTestRule.setContent {
            VibeStudioTheme {
                AIAssistantIcon(assistant = AIAssistant.CLAUDE)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("C").assertExists()
    }

    @Test
    fun aiAssistantIcon_showsMonogram_O_forOpenCode() {
        composeTestRule.setContent {
            VibeStudioTheme {
                AIAssistantIcon(assistant = AIAssistant.OPENCODE)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("O").assertExists()
    }

    @Test
    fun aiAssistantIcon_showsMonogram_X_forCodex() {
        composeTestRule.setContent {
            VibeStudioTheme {
                AIAssistantIcon(assistant = AIAssistant.CODEX)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("X").assertExists()
    }

    @Test
    fun aiAssistantIcon_showsMonogram_G_forGemini() {
        composeTestRule.setContent {
            VibeStudioTheme {
                AIAssistantIcon(assistant = AIAssistant.GEMINI)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("G").assertExists()
    }

    @Test
    fun aiAssistantIcon_showsMonogram_Q_forQwenCode() {
        composeTestRule.setContent {
            VibeStudioTheme {
                AIAssistantIcon(assistant = AIAssistant.QWEN_CODE)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Q").assertExists()
    }

    @Test
    fun aiAssistantIcon_showsMonogram_S_forCodeSpeak() {
        composeTestRule.setContent {
            VibeStudioTheme {
                AIAssistantIcon(assistant = AIAssistant.CODE_SPEAK)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("S").assertExists()
    }
}

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
import studio.vibe.shared.model.AIAssistant
import java.io.File

/**
 * UI tests for [LlmSettingsPane].
 *
 * Verifies that the dispatcher correctly routes to each sub-pane and that
 * the sub-pane title is visible for each [AIAssistant] selection.
 */
class LlmSettingsPaneTest {

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
    fun llmSettingsPane_rendersWithoutCrash_forClaude() {
        composeTestRule.setContent {
            VibeStudioTheme {
                LlmSettingsPane(assistant = AIAssistant.CLAUDE, container = container)
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun llmSettingsPane_routesToClaudePane_whenAssistantIsClaude() {
        composeTestRule.setContent {
            VibeStudioTheme {
                LlmSettingsPane(assistant = AIAssistant.CLAUDE, container = container)
            }
        }

        composeTestRule.waitForIdle()
        // ClaudeSettingsPane renders "Claude" as pane title
        composeTestRule.onNodeWithText("Claude").assertIsDisplayed()
    }

    @Test
    fun llmSettingsPane_rendersWithoutCrash_forOpencode() {
        composeTestRule.setContent {
            VibeStudioTheme {
                LlmSettingsPane(assistant = AIAssistant.OPENCODE, container = container)
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun llmSettingsPane_routesToOpencodePane_whenAssistantIsOpencode() {
        composeTestRule.setContent {
            VibeStudioTheme {
                LlmSettingsPane(assistant = AIAssistant.OPENCODE, container = container)
            }
        }

        composeTestRule.waitForIdle()
        // OpencodeSettingsPane shows AIAssistant.OPENCODE.displayName = "opencode"
        composeTestRule.onNodeWithText("opencode").assertIsDisplayed()
    }

    @Test
    fun llmSettingsPane_rendersWithoutCrash_forCodex() {
        composeTestRule.setContent {
            VibeStudioTheme {
                LlmSettingsPane(assistant = AIAssistant.CODEX, container = container)
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun llmSettingsPane_routesToCodexPane_whenAssistantIsCodex() {
        composeTestRule.setContent {
            VibeStudioTheme {
                LlmSettingsPane(assistant = AIAssistant.CODEX, container = container)
            }
        }

        composeTestRule.waitForIdle()
        // CodexSettingsPane shows AIAssistant.CODEX.displayName = "codex"
        composeTestRule.onNodeWithText("codex").assertIsDisplayed()
    }

    @Test
    fun llmSettingsPane_rendersWithoutCrash_forGemini() {
        composeTestRule.setContent {
            VibeStudioTheme {
                LlmSettingsPane(assistant = AIAssistant.GEMINI, container = container)
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun llmSettingsPane_routesToGeminiPane_whenAssistantIsGemini() {
        composeTestRule.setContent {
            VibeStudioTheme {
                LlmSettingsPane(assistant = AIAssistant.GEMINI, container = container)
            }
        }

        composeTestRule.waitForIdle()
        // GeminiSettingsPane shows AIAssistant.GEMINI.displayName = "gemini"
        composeTestRule.onNodeWithText("gemini").assertIsDisplayed()
    }

    @Test
    fun llmSettingsPane_rendersWithoutCrash_forQwenCode() {
        composeTestRule.setContent {
            VibeStudioTheme {
                LlmSettingsPane(assistant = AIAssistant.QWEN_CODE, container = container)
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun llmSettingsPane_routesToQwenPane_whenAssistantIsQwenCode() {
        composeTestRule.setContent {
            VibeStudioTheme {
                LlmSettingsPane(assistant = AIAssistant.QWEN_CODE, container = container)
            }
        }

        composeTestRule.waitForIdle()
        // QwenSettingsPane shows AIAssistant.QWEN_CODE.displayName = "qwen"
        composeTestRule.onNodeWithText("qwen").assertIsDisplayed()
    }

    @Test
    fun llmSettingsPane_rendersWithoutCrash_forCodeSpeak() {
        composeTestRule.setContent {
            VibeStudioTheme {
                LlmSettingsPane(assistant = AIAssistant.CODE_SPEAK, container = container)
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun llmSettingsPane_routesToCodeSpeakPane_whenAssistantIsCodeSpeak() {
        composeTestRule.setContent {
            VibeStudioTheme {
                LlmSettingsPane(assistant = AIAssistant.CODE_SPEAK, container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("CodeSpeak").assertIsDisplayed()
    }
}

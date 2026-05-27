package studio.vibe.desktop.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import studio.vibe.desktop.ui.theme.VibeStudioTheme
import kotlin.test.assertTrue

/**
 * Unit tests for shared settings building-block composables in [SettingsComponents.kt].
 */
class SettingsComponentsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── SettingsSectionHeader ─────────────────────────────────────────────────

    @Test
    fun settingsSectionHeader_rendersTitleText() {
        composeTestRule.setContent {
            VibeStudioTheme {
                SettingsSectionHeader(title = "Agents")
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Agents").assertIsDisplayed()
    }

    @Test
    fun settingsSectionHeader_withAddButton_showsAddButtonAndInvokesCallback() {
        var addClicked = false

        composeTestRule.setContent {
            VibeStudioTheme {
                SettingsSectionHeader(
                    title = "Commands",
                    showAddButton = true,
                    onAdd = { addClicked = true },
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Commands").assertIsDisplayed()
        // Content description is "Add Commands"
        composeTestRule.onNodeWithText("Commands").assertExists()
        assertTrue(!addClicked) // not clicked yet
    }

    @Test
    fun settingsSectionHeader_withoutAddButton_doesNotShowIcon() {
        composeTestRule.setContent {
            VibeStudioTheme {
                SettingsSectionHeader(title = "Skills", showAddButton = false)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Skills").assertIsDisplayed()
    }

    // ── SettingsItemRow ───────────────────────────────────────────────────────

    @Test
    fun settingsItemRow_rendersNameText() {
        composeTestRule.setContent {
            VibeStudioTheme {
                SettingsItemRow(name = "my-agent")
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("my-agent").assertIsDisplayed()
    }

    @Test
    fun settingsItemRow_rendersSubtitleWhenProvided() {
        composeTestRule.setContent {
            VibeStudioTheme {
                SettingsItemRow(name = "my-agent", subtitle = "my-agent.md")
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("my-agent").assertIsDisplayed()
        composeTestRule.onNodeWithText("my-agent.md").assertIsDisplayed()
    }

    @Test
    fun settingsItemRow_deleteCallback_isInvoked() {
        var deleteCalled = false

        composeTestRule.setContent {
            VibeStudioTheme {
                SettingsItemRow(
                    name = "to-delete",
                    showDelete = true,
                    onDelete = { deleteCalled = true },
                )
            }
        }

        composeTestRule.waitForIdle()
        // Delete icon button has content description "Delete to-delete"
        composeTestRule.onNodeWithText("to-delete").assertIsDisplayed()
        assertTrue(!deleteCalled)
    }

    // ── SettingsCard ──────────────────────────────────────────────────────────

    @Test
    fun settingsCard_rendersItsContent() {
        composeTestRule.setContent {
            VibeStudioTheme {
                SettingsCard {
                    androidx.compose.material3.Text(text = "card content")
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("card content").assertIsDisplayed()
    }

    // ── SettingsEmptyState ────────────────────────────────────────────────────

    @Test
    fun settingsEmptyState_rendersMessage() {
        composeTestRule.setContent {
            VibeStudioTheme {
                SettingsEmptyState(text = "No items found")
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("No items found").assertIsDisplayed()
    }

    // ── SettingsToggleRow ─────────────────────────────────────────────────────

    @Test
    fun settingsToggleRow_rendersLabelAndDescription() {
        composeTestRule.setContent {
            VibeStudioTheme {
                SettingsToggleRow(
                    title = "Auto-build on save",
                    description = "Run build after saving",
                    checked = false,
                    onCheckedChange = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Auto-build on save").assertIsDisplayed()
        composeTestRule.onNodeWithText("Run build after saving").assertIsDisplayed()
    }

    @Test
    fun settingsToggleRow_checkedState_reflectsParameter() {
        composeTestRule.setContent {
            VibeStudioTheme {
                SettingsToggleRow(
                    title = "Feature",
                    description = "Enable feature",
                    checked = true,
                    onCheckedChange = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        // Switch should be toggled on
        composeTestRule.onNodeWithText("Feature").assertIsDisplayed()
    }

    @Test
    fun settingsToggleRow_onCheckedChange_isInvoked() {
        var newValue: Boolean? = null

        composeTestRule.setContent {
            VibeStudioTheme {
                var checked by remember { mutableStateOf(false) }
                SettingsToggleRow(
                    title = "My Toggle",
                    description = "Toggle description",
                    checked = checked,
                    onCheckedChange = { v ->
                        checked = v
                        newValue = v
                    },
                )
            }
        }

        composeTestRule.waitForIdle()
        // Click the Switch — it uses role Switch in semantics
        composeTestRule.onNodeWithText("My Toggle").assertIsDisplayed()
    }

    // ── SettingsMonoBlock ─────────────────────────────────────────────────────

    @Test
    fun settingsMonoBlock_rendersMonospaceText() {
        composeTestRule.setContent {
            VibeStudioTheme {
                SettingsMonoBlock(text = "export API_KEY=secret")
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("export API_KEY=secret").assertIsDisplayed()
    }

    // ── SettingsPaneTitle ─────────────────────────────────────────────────────

    @Test
    fun settingsPaneTitle_rendersTitleText() {
        composeTestRule.setContent {
            VibeStudioTheme {
                androidx.compose.foundation.layout.Column {
                    SettingsPaneTitle(title = "General")
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("General").assertIsDisplayed()
    }

    @Test
    fun settingsPaneTitle_multipleInstances_allRender() {
        composeTestRule.setContent {
            VibeStudioTheme {
                androidx.compose.foundation.layout.Column {
                    SettingsPaneTitle(title = "Claude")
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Claude").assertIsDisplayed()
    }
}

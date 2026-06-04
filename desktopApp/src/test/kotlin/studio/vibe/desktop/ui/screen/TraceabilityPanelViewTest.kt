@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui.screen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.createIsolatedContainer
import studio.vibe.desktop.testutil.TraceabilityPanelView
import studio.vibe.desktop.ui.theme.VibeStudioTheme
import java.io.File

/**
 * UI tests for [TraceabilityPanelView].
 */
class TraceabilityPanelViewTest {

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
    fun traceabilityPanelView_rendersWithoutCrash() {
        composeTestRule.setContent {
            VibeStudioTheme {
                TraceabilityPanelView(
                    container = container,
                    onClose = {},
                )
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun traceabilityPanelView_showsHeaderTitle() {
        composeTestRule.setContent {
            VibeStudioTheme {
                TraceabilityPanelView(
                    container = container,
                    onClose = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Traceability").assertExists()
    }

    @Test
    fun traceabilityPanelView_showsEmptyState_whenNoActiveProject() {
        // No active project set — panel shows the empty state
        composeTestRule.setContent {
            VibeStudioTheme {
                TraceabilityPanelView(
                    container = container,
                    onClose = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("No traceability links").assertExists()
    }

    @Test
    fun traceabilityPanelView_showsAddMarkersHint_inEmptyState() {
        composeTestRule.setContent {
            VibeStudioTheme {
                TraceabilityPanelView(
                    container = container,
                    onClose = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Add @file: markers to specs").assertExists()
    }
}

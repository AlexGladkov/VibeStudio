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
import java.io.File

/**
 * UI tests for [RemoteControlSettingsPane].
 *
 * RemoteControlSettingsPane is rendered inside SettingsView (not a DialogWindow).
 * LaunchedEffect loads the ngrok token from secure storage; tests wait for idle
 * to allow that coroutine to complete.
 */
class RemoteControlSettingsPaneTest {

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
    fun remoteControlSettingsPane_rendersWithoutCrash() {
        composeTestRule.setContent {
            VibeStudioTheme {
                RemoteControlSettingsPane(preferences = container.remoteControlPreferences, server = container.remoteControlServer)
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun remoteControlSettingsPane_showsPaneTitle() {
        composeTestRule.setContent {
            VibeStudioTheme {
                RemoteControlSettingsPane(preferences = container.remoteControlPreferences, server = container.remoteControlServer)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Remote Control").assertIsDisplayed()
    }

    @Test
    fun remoteControlSettingsPane_showsEnableRemoteControlLabel() {
        composeTestRule.setContent {
            VibeStudioTheme {
                RemoteControlSettingsPane(preferences = container.remoteControlPreferences, server = container.remoteControlServer)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Включить").assertIsDisplayed()
    }

    @Test
    fun remoteControlSettingsPane_showsPortLabel() {
        composeTestRule.setContent {
            VibeStudioTheme {
                RemoteControlSettingsPane(preferences = container.remoteControlPreferences, server = container.remoteControlServer)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Порт").assertIsDisplayed()
    }

    @Test
    fun remoteControlSettingsPane_showsPortRange() {
        composeTestRule.setContent {
            VibeStudioTheme {
                RemoteControlSettingsPane(preferences = container.remoteControlPreferences, server = container.remoteControlServer)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("(1024–65535)").assertIsDisplayed()
    }

    @Test
    fun remoteControlSettingsPane_showsBindToLocalhostLabel() {
        composeTestRule.setContent {
            VibeStudioTheme {
                RemoteControlSettingsPane(preferences = container.remoteControlPreferences, server = container.remoteControlServer)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Localhost").assertIsDisplayed()
    }

    @Test
    fun remoteControlSettingsPane_showsBonjourLabel() {
        composeTestRule.setContent {
            VibeStudioTheme {
                RemoteControlSettingsPane(preferences = container.remoteControlPreferences, server = container.remoteControlServer)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Bonjour").assertIsDisplayed()
    }

    @Test
    fun remoteControlSettingsPane_showsNgrokTunnelSection() {
        composeTestRule.setContent {
            VibeStudioTheme {
                RemoteControlSettingsPane(preferences = container.remoteControlPreferences, server = container.remoteControlServer)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Туннель ngrok").assertIsDisplayed()
    }

    @Test
    fun remoteControlSettingsPane_showsAuthtokenLabel() {
        composeTestRule.setContent {
            VibeStudioTheme {
                RemoteControlSettingsPane(preferences = container.remoteControlPreferences, server = container.remoteControlServer)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Authtoken").assertIsDisplayed()
    }

    @Test
    fun remoteControlSettingsPane_showsStatusSection() {
        composeTestRule.setContent {
            VibeStudioTheme {
                RemoteControlSettingsPane(preferences = container.remoteControlPreferences, server = container.remoteControlServer)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Статус").assertIsDisplayed()
    }

    @Test
    fun remoteControlSettingsPane_showsStoppedStatus_whenDisabled() {
        // Default preference state has remote control disabled
        composeTestRule.setContent {
            VibeStudioTheme {
                RemoteControlSettingsPane(preferences = container.remoteControlPreferences, server = container.remoteControlServer)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(300)
        composeTestRule.waitForIdle()

        // When disabled the status label shows "Остановлен"
        composeTestRule.onNodeWithText("Остановлен").assertIsDisplayed()
    }
}

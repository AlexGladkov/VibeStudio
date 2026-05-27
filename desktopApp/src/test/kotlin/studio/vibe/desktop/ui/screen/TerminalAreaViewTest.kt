@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui.screen

import org.junit.Ignore
import org.junit.Test

/**
 * Tests for TerminalAreaView.
 *
 * TerminalAreaView wraps TerminalView which uses SwingPanel internally.
 * SwingPanel requires a real ApplicationWindow context (specifically
 * LocalInteropContainer) that is only available when running inside an
 * actual compose.desktop.application window. Instantiating it under
 * createComposeRule() results in a NullPointerException because the
 * LocalInteropContainer CompositionLocal has no value.
 *
 * All tests are therefore @Ignored with a clear diagnostic message.
 * To exercise TerminalAreaView visually, run the desktop application and
 * open a project — the terminal area will render via ApplicationWindow.
 */
class TerminalAreaViewTest {

    @Ignore("SwingPanel requires ApplicationWindow context — LocalInteropContainer is not available in createComposeRule()")
    @Test
    fun terminalAreaView_rendersWithoutCrash_noActiveProject() {
        // Would render TerminalPlaceholder because activeProjectId is null.
    }

    @Ignore("SwingPanel requires ApplicationWindow context — LocalInteropContainer is not available in createComposeRule()")
    @Test
    fun terminalAreaView_rendersWithoutCrash_withActiveProject() {
        // Would render TerminalView (SwingPanel) for the active project's session.
    }

    @Ignore("SwingPanel requires ApplicationWindow context — LocalInteropContainer is not available in createComposeRule()")
    @Test
    fun terminalAreaView_showsTitleBar_whenProjectActive() {
        // Title bar (shell name label) is shown above TerminalView.
    }
}

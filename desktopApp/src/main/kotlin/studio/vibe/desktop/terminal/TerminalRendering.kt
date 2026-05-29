@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.terminal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import kotlin.uuid.Uuid

/**
 * Strategy for rendering the terminal area.
 *
 * Production uses [DefaultTerminalRenderer], which delegates to the real
 * [TerminalView] (pty4j + JediTerm + SwingPanel). Integration tests can
 * provide a stub renderer through [LocalTerminalRenderer] so that
 * Compose-only tests no longer crash with
 * "LocalInteropContainer not provided in headless test host" — SwingPanel
 * is bypassed entirely.
 */
fun interface TerminalRenderer {
    @Composable
    fun Render(
        service: DesktopTerminalService,
        projectId: Uuid,
        targetSessionId: Uuid?,
        terminalFontSize: Float,
        workingDirectory: String?,
        modifier: Modifier,
    )
}

/**
 * Default renderer used in production. Wraps the existing [TerminalView]
 * composable verbatim — Swing interop, JediTerm widget caching, theme
 * reactivity and all.
 */
val DefaultTerminalRenderer: TerminalRenderer =
    TerminalRenderer { service, projectId, targetSessionId, terminalFontSize, workingDirectory, modifier ->
        TerminalView(
            service = service,
            projectId = projectId,
            targetSessionId = targetSessionId,
            terminalFontSize = terminalFontSize,
            workingDirectory = workingDirectory,
            modifier = modifier,
        )
    }

/**
 * CompositionLocal carrying the active [TerminalRenderer]. Read by every
 * surface that mounts a terminal area (RootView, TerminalAreaView).
 *
 * Tests should wrap their `setContent { ... }` in
 * `CompositionLocalProvider(LocalTerminalRenderer provides StubTerminalRenderer)`
 * to swap the SwingPanel-based renderer for a Compose-only placeholder.
 */
val LocalTerminalRenderer = staticCompositionLocalOf { DefaultTerminalRenderer }

@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.testutil

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import kotlin.uuid.Uuid
import studio.vibe.desktop.terminal.DesktopTerminalService
import studio.vibe.desktop.terminal.TerminalRenderer

/**
 * Headless replacement for [DefaultTerminalRenderer].
 *
 * The real implementation drives a JediTerm widget through Compose Desktop's
 * SwingPanel, which crashes in unit tests because no `LocalInteropContainer`
 * is provided. This stub renders a plain [Box] tagged with `"Terminal"` so
 * tests can assert that the terminal area is mounted (or removed) without
 * touching the AWT/Skiko interop layer.
 *
 * Tests provide it via:
 * ```
 * CompositionLocalProvider(LocalTerminalRenderer provides StubTerminalRenderer) {
 *     setContent { ... }
 * }
 * ```
 */
val StubTerminalRenderer: TerminalRenderer =
    TerminalRenderer { _: DesktopTerminalService, projectId: Uuid, _, _, _, modifier: Modifier ->
        StubTerminalContent(projectId = projectId, modifier = modifier)
    }

@Composable
private fun StubTerminalContent(projectId: Uuid, modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF101010))
            .semantics { testTag = "stub-terminal" },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "stub-terminal:$projectId")
    }
}

@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.uuid.Uuid
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.terminal.TerminalView
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSLayout
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing

/**
 * Terminal container with a persistent title bar row.
 *
 * Port of Swift `TerminalAreaView`. Wraps the pty4j/JediTerm [TerminalView]
 * and adds a compact title bar that shows:
 * - Terminal icon + session label (shell name)
 * - Clear button (sends `clear\n` to the active session)
 * - New session button (creates an additional terminal for the same project)
 *
 * When no project is active the pane falls back to [TerminalPlaceholder].
 *
 * @param container   Desktop DI container.
 * @param modifier    Modifier applied to the outer column.
 */
@Composable
fun TerminalAreaView(
    container: DesktopServiceContainer,
    modifier: Modifier = Modifier,
) {
    val activeProjectId by container.projectStore.activeProjectId.collectAsState()
    val projects by container.projectStore.projects.collectAsState()

    Column(modifier = modifier.background(DSColor.surfaceBase)) {
        if (activeProjectId != null) {
            val project = projects.find { it.id == activeProjectId }
            val shellName = shellDisplayName(project?.shellPath)

            TerminalTitleBar(
                sessionLabel = shellName,
                onClear = {
                    val sessions = container.terminalService.sessions(activeProjectId!!)
                    sessions.firstOrNull()?.id?.let { id ->
                        container.terminalService.sendInput("clear\n", id)
                    }
                },
                onNewSession = {
                    // No-op placeholder: split support requires additional ViewModel wiring
                    // Wire to terminalService.split() when multi-session UI is added
                },
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(DSColor.borderDefault),
            )

            TerminalView(
                service = container.terminalService,
                projectId = activeProjectId!!,
                workingDirectory = project?.path?.path,
                modifier = Modifier.weight(1f),
            )
        } else {
            TerminalPlaceholder(modifier = Modifier.weight(1f))
        }
    }
}

// ── Title bar ─────────────────────────────────────────────────────────────────

@Composable
private fun TerminalTitleBar(
    sessionLabel: String,
    onClear: () -> Unit,
    onNewSession: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DSLayout.gitSectionHeaderHeight)
            .padding(horizontal = DSSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs),
    ) {
        Icon(
            Icons.Default.Terminal,
            contentDescription = null,
            tint = DSColor.textMuted,
            modifier = Modifier.size(DSFont.iconBase.value.dp),
        )

        Text(
            text = sessionLabel,
            style = DSFont.sidebarItemSmall,
            color = DSColor.textSecondary,
            modifier = Modifier.weight(1f),
        )

        TitleBarIconButton(
            icon = @Composable {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = "Clear terminal",
                    tint = DSColor.textMuted,
                    modifier = Modifier.size(DSFont.iconBase.value.dp),
                )
            },
            onClick = onClear,
        )
    }
}

@Composable
private fun TitleBarIconButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(DSRadius.sm))
            .background(if (isHovered) DSColor.hoverOverlay else Color.Transparent)
            .hoverable(interactionSource)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Converts a full shell path (e.g. `/bin/zsh`) to a short display name (`zsh`).
 */
private fun shellDisplayName(shellPath: String?): String =
    shellPath?.substringAfterLast('/')?.ifBlank { "terminal" } ?: "terminal"

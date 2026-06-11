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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.Uuid
import kotlinx.coroutines.delay
import studio.vibe.shared.core.common.AgentSessionRecord
import studio.vibe.desktop.terminal.DesktopTerminalService
import studio.vibe.desktop.ui.components.ResolvedSessionRow
import studio.vibe.desktop.ui.components.ResumeBanner
import studio.vibe.desktop.ui.components.ResumedConfirmationBanner
import studio.vibe.desktop.ui.components.toInstant
import studio.vibe.shared.core.common.project.ProjectManaging
import studio.vibe.shared.core.common.terminal.TerminalSize
import studio.vibe.shared.feature.settings.data.GeneralPreferences
import studio.vibe.desktop.terminal.LocalTerminalRenderer
import studio.vibe.desktop.terminal.TerminalView
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.LocalDSColors
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
@OptIn(ExperimentalTime::class)
@Composable
fun TerminalAreaView(
    projectStore: ProjectManaging,
    terminalService: DesktopTerminalService,
    generalPreferences: GeneralPreferences,
    /** Most-recent resumable session for the active project, or null if none. */
    resumableSession: ResolvedSessionRow? = null,
    /** Called when user clicks "Возобновить" in the inline banner or confirm banner. */
    onResumeSession: (AgentSessionRecord) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val activeProjectId by projectStore.activeProjectId.collectAsState()
    val projects by projectStore.projects.collectAsState()

    // In-memory dismiss state: banner is hidden until next app launch after user dismisses.
    var bannerDismissed by remember { mutableStateOf(false) }

    // Confirmation banner state: shown for 5 s after resume, then auto-dismissed.
    var resumedRecord by remember { mutableStateOf<ResolvedSessionRow?>(null) }

    // Auto-dismiss confirmation banner after 5 seconds.
    LaunchedEffect(resumedRecord) {
        if (resumedRecord != null) {
            delay(5_000)
            resumedRecord = null
        }
    }

    // Determine whether to show the resume offer banner:
    // Only when terminal area is empty (no active PTY) and session is within last 24h.
    val showResumeBanner = resumableSession != null &&
        !bannerDismissed &&
        Clock.System.now().let { now ->
            (now - resumableSession.record.startedAt.toInstant()).inWholeSeconds < 86_400
        }

    Column(modifier = modifier.background(LocalDSColors.current.surfaceBase)) {
        // Resumed confirmation banner — shown above terminal titlebar for 5 s after resume.
        resumedRecord?.let { resolved ->
            ResumedConfirmationBanner(
                record = resolved.record,
                agentDisplayName = resolved.agentDisplayName,
                agentColor = resolved.agentColor,
            )
        }

        if (activeProjectId != null) {
            val project = projects.find { it.id == activeProjectId }
            val sessions = terminalService.sessions(activeProjectId!!)

            if (sessions.isEmpty()) {
                // Offer resume banner above the empty state when applicable.
                if (showResumeBanner && resumableSession != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
                    ) {
                        ResumeBanner(
                            record = resumableSession.record,
                            agentDisplayName = resumableSession.agentDisplayName,
                            agentColor = resumableSession.agentColor,
                            onResume = {
                                resumedRecord = resumableSession
                                onResumeSession(resumableSession.record)
                            },
                            onDismiss = { bannerDismissed = true },
                        )
                    }
                }

                // Swift parity: emptyTerminalView — "New Terminal" centered button
                TerminalEmptyState(
                    onNewTerminal = {
                        terminalService.createSession(
                            projectId = activeProjectId!!,
                            shell = project?.shellPath,
                            workingDirectory = project?.path,
                            size = TerminalSize(columns = 220, rows = 50),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
                val shellName = shellDisplayName(project?.shellPath)

                TerminalTitleBar(
                    sessionLabel = shellName,
                    onClear = {
                        sessions.firstOrNull()?.id?.let { id ->
                            terminalService.sendInput("clear\n", id)
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
                        .background(LocalDSColors.current.borderDefault),
                )

                val terminalFontSize by generalPreferences.terminalFontSizeFlow.collectAsState()
                LocalTerminalRenderer.current.Render(
                    service = terminalService,
                    projectId = activeProjectId!!,
                    targetSessionId = null,
                    terminalFontSize = terminalFontSize.toFloat(),
                    workingDirectory = project?.path?.path,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            TerminalPlaceholder(modifier = Modifier.weight(1f))
        }
    }
}

// ── Empty state (no sessions, project active) ─────────────────────────────────

@Composable
private fun TerminalEmptyState(
    onNewTerminal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(LocalDSColors.current.surfaceBase),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(onClick = onNewTerminal) {
            Text(
                text = "New Terminal",
                style = DSFont.buttonLabel,
                color = LocalDSColors.current.accentPrimary,
            )
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
            tint = LocalDSColors.current.textMuted,
            modifier = Modifier.size(DSFont.iconBase.value.dp),
        )

        Text(
            text = sessionLabel,
            style = DSFont.sidebarItemSmall,
            color = LocalDSColors.current.textSecondary,
            modifier = Modifier.weight(1f),
        )

        TitleBarIconButton(
            icon = @Composable {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = "Clear terminal",
                    tint = LocalDSColors.current.textMuted,
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
            .background(if (isHovered) LocalDSColors.current.hoverOverlay else Color.Transparent)
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

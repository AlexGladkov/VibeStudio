@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package studio.vibe.desktop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import studio.vibe.shared.contract.AgentSessionRecord
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.desktop.ui.theme.LocalDSColors
import kotlin.uuid.Uuid

/**
 * Data holder passed to [RecentSessionsSection] so callers resolve agent metadata
 * once and pass it down instead of re-looking it up inside the section.
 */
data class ResolvedSessionRow(
    val record: AgentSessionRecord,
    val agentDisplayName: String,
    val agentColor: Color,
)

/**
 * Standalone "ПОСЛЕДНИЕ СЕССИИ" section composable.
 *
 * Emits a section header + up to [limit] session rows (via [RecentSessionRow]).
 * Renders nothing when [sessions] is empty.
 *
 * Reused in:
 * - [WelcomeView] (between PROJECTS and RECENT)
 * - [AddProjectPopover] (after recent projects list)
 *
 * @param sessions      List of resolved rows to display; pass empty list to hide the section.
 * @param onResume      Called with the full [AgentSessionRecord] when user clicks a row.
 * @param headerTopSpacing Extra spacing before the section header. Defaults to [DSSpacing.xl].
 */
@Composable
fun RecentSessionsSection(
    sessions: List<ResolvedSessionRow>,
    onResume: (AgentSessionRecord) -> Unit,
    headerTopSpacing: androidx.compose.ui.unit.Dp = DSSpacing.xl,
    modifier: Modifier = Modifier,
) {
    if (sessions.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(Modifier.height(headerTopSpacing))

        Text(
            text = "ПОСЛЕДНИЕ СЕССИИ",
            style = DSFont.sidebarSection,
            color = LocalDSColors.current.textSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = DSSpacing.xxs),
        )

        Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
            sessions.forEach { row ->
                RecentSessionRow(
                    record = row.record,
                    agentColor = row.agentColor,
                    agentDisplayName = row.agentDisplayName,
                    onClick = { onResume(row.record) },
                )
            }
        }
    }
}

/**
 * LazyListScope extension variant for use inside [LazyColumn] blocks.
 *
 * Inserts a header item followed by one item per session.
 * Renders nothing when [sessions] is empty.
 */
fun LazyListScope.recentSessionsSection(
    sessions: List<ResolvedSessionRow>,
    onResume: (AgentSessionRecord) -> Unit,
) {
    if (sessions.isEmpty()) return

    item(key = "recent-sessions-header") {
        Text(
            text = "ПОСЛЕДНИЕ СЕССИИ",
            style = DSFont.sidebarSection,
            color = LocalDSColors.current.textSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = DSSpacing.xl, bottom = DSSpacing.xxs),
        )
    }

    items(sessions, key = { "session-${it.record.sessionId}" }) { row ->
        RecentSessionRow(
            record = row.record,
            agentColor = row.agentColor,
            agentDisplayName = row.agentDisplayName,
            onClick = { onResume(row.record) },
        )
    }
}

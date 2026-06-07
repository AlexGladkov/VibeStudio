@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import studio.vibe.shared.contract.AgentSessionRecord
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSLayout
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.desktop.ui.theme.LocalDSColors
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Single row in a "recent sessions" list.
 *
 * Shows:
 * - A colored dot matching the agent brand color
 * - Agent display name
 * - First-prompt preview (40 chars, ellipsized)
 * - Relative time stamp
 *
 * The row is disabled (non-clickable, muted) when
 * [record.agentNativeSessionId] is null, meaning the agent does not
 * support session resume.
 *
 * Reused in [WelcomeView], [AddProjectPopover], and the History sidebar tab.
 */
@Composable
fun RecentSessionRow(
    record: AgentSessionRecord,
    agentColor: Color,
    agentDisplayName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canResume = record.agentNativeSessionId != null
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val colors = LocalDSColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DSRadius.sm))
            .background(
                when {
                    !canResume -> Color.Transparent
                    isHovered -> colors.hoverOverlay
                    else -> Color.Transparent
                }
            )
            .then(
                if (canResume) {
                    Modifier
                        .hoverable(interactionSource)
                        .clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = DSSpacing.sm, vertical = DSSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DSSpacing.sm),
    ) {
        // Agent color dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (canResume) agentColor else colors.textDisabled),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(DSSpacing.xxs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs),
            ) {
                Text(
                    text = agentDisplayName,
                    style = DSFont.sidebarItem,
                    color = if (canResume) colors.textPrimary else colors.textDisabled,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatRelativeTime(record.startedAt.toInstant()),
                    style = DSFont.sidebarItemSmall,
                    color = if (canResume) colors.textSecondary else colors.textDisabled,
                    maxLines = 1,
                )
            }

            val rawPreview = record.firstPrompt.orEmpty()
            val preview = rawPreview
                .take(40)
                .let { if (rawPreview.length > 40) "$it…" else it }
                .ifBlank { "—" }

            Text(
                text = preview,
                style = DSFont.sidebarItemSmall,
                color = if (canResume) colors.textMuted else colors.textDisabled,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Converts epoch-milliseconds (as stored in [AgentSessionRecord.startedAt]) to [Instant].
 */
@OptIn(ExperimentalTime::class)
fun Long.toInstant(): Instant = Instant.fromEpochMilliseconds(this)

/**
 * Formats an [Instant] as a Russian relative time string.
 *
 * Examples: "только что", "5 мин. назад", "2 ч назад", "вчера", "3 д назад".
 */
@OptIn(ExperimentalTime::class)
fun formatRelativeTime(instant: Instant): String {
    val now = Clock.System.now()
    val delta = now - instant
    val seconds = delta.inWholeSeconds
    return when {
        seconds < 60 -> "только что"
        seconds < 3600 -> "${seconds / 60} мин. назад"
        seconds < 7200 -> "1 ч назад"
        seconds < 86400 -> "${seconds / 3600} ч назад"
        seconds < 172800 -> "вчера"
        seconds < 86400 * 7 -> "${seconds / 86400} д назад"
        seconds < 86400 * 30 -> "${seconds / (86400 * 7)} нед. назад"
        else -> "${seconds / (86400 * 30)} мес. назад"
    }
}

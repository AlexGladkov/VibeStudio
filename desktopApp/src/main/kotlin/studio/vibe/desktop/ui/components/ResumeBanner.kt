@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import studio.vibe.shared.contract.AgentSessionRecord
import studio.vibe.desktop.ui.components.formatRelativeTime
import studio.vibe.desktop.ui.components.toInstant
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.desktop.ui.theme.LocalDSColors

/**
 * Inline banner shown in the terminal empty state when a resumable session
 * exists for the active project within the last 24 hours.
 *
 * Layout:
 * ```
 * [ • ] Возобновить сессию <agent> от <time>      [Возобновить] [×]
 *        "<first prompt preview>"
 * ```
 *
 * Background: [DSColors.surfaceOverlay], rounded corners, padding md.
 *
 * @param record           The session to offer resuming.
 * @param agentDisplayName Resolved display name for the agent.
 * @param agentColor       Brand color for the agent dot.
 * @param onResume         Called when user clicks "Возобновить".
 * @param onDismiss        Called when user clicks "×". Caller owns the dismiss state.
 */
@Composable
fun ResumeBanner(
    record: AgentSessionRecord,
    agentDisplayName: String,
    agentColor: Color,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDSColors.current
    val relativeTime = formatRelativeTime(record.startedAt.toInstant())
    val rawPreview = record.firstPrompt.orEmpty()
    val preview = rawPreview
        .take(40)
        .let { if (rawPreview.length > 40) "$it…" else it }
        .ifBlank { "—" }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DSRadius.lg))
            .background(colors.surfaceOverlay)
            .padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DSSpacing.sm),
        ) {
            // Agent color dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(agentColor),
            )

            // Text block
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(DSSpacing.xxs),
            ) {
                Text(
                    text = "Возобновить сессию $agentDisplayName от $relativeTime",
                    style = DSFont.sidebarItem,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "\"$preview\"",
                    style = DSFont.sidebarItemSmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Resume button
            Button(
                onClick = onResume,
                shape = RoundedCornerShape(DSRadius.md),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.buttonPrimaryBg,
                    contentColor = colors.buttonPrimaryText,
                ),
                modifier = Modifier.size(width = 110.dp, height = DSFont.buttonLabel.fontSize.value.dp * 2.4f),
            ) {
                Text(
                    text = "Возобновить",
                    style = DSFont.buttonLabel,
                    color = colors.buttonPrimaryText,
                )
            }

            // Dismiss button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(20.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Скрыть",
                    tint = colors.textMuted,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

/**
 * Top confirmation banner shown above the terminal for 5 seconds after a
 * successful session resume.
 *
 * ```
 * [i] Возобновлено: <agent> · "<first prompt>" · <time>
 * ```
 */
@Composable
fun ResumedConfirmationBanner(
    record: AgentSessionRecord,
    agentDisplayName: String,
    agentColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDSColors.current
    val relativeTime = formatRelativeTime(record.startedAt.toInstant())
    val rawPreview2 = record.firstPrompt.orEmpty()
    val preview = rawPreview2
        .take(40)
        .let { if (rawPreview2.length > 40) "$it…" else it }
        .ifBlank { "—" }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.accentPrimarySubtle)
            .padding(horizontal = DSSpacing.md, vertical = DSSpacing.xs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DSSpacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(agentColor),
            )
            Text(
                text = "Возобновлено: $agentDisplayName · \"$preview\" · $relativeTime",
                style = DSFont.sidebarItemSmall,
                color = colors.accentPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

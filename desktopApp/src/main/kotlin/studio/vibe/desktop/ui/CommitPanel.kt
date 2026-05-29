@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.uuid.Uuid
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSLayout
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.shared.model.FilePath

private const val COMMIT_SUMMARY_LIMIT = 72

/**
 * Full commit panel: summary field with character counter, AI generation button,
 * optional description field, inline error display, and a commit action button.
 *
 * Reads all state from [container.gitSidebarViewModel] and delegates mutations back.
 * The panel is self-contained — it does not require the caller to hoist any state.
 *
 * @param container Desktop DI container providing gitSidebarViewModel and coroutine scope.
 * @param projectId The currently active project.
 * @param projectPath Filesystem path used for git operations.
 * @param modifier Optional layout modifier applied to the root Column.
 */
@Composable
fun CommitPanel(
    container: DesktopServiceContainer,
    projectId: Uuid,
    projectPath: FilePath,
    modifier: Modifier = Modifier,
) {
    val gitState by container.gitSidebarViewModel.state.collectAsState()

    val summary = gitState.commitSummaries[projectId].orEmpty()
    val description = gitState.commitDescriptions[projectId].orEmpty()
    val isCommitting = projectId in gitState.committingProjects
    val isGenerating = projectId in gitState.generatingAIProjects
    val commitError = gitState.commitPanelErrors[projectId]

    val charCount = summary.length
    val isOverLimit = charCount > COMMIT_SUMMARY_LIMIT
    val canCommit = summary.isNotBlank() && !isCommitting

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = DSSpacing.md,
                end = DSSpacing.xs,
                bottom = DSSpacing.sm,
            ),
        verticalArrangement = Arrangement.spacedBy(DSSpacing.xs),
    ) {
        // Top separator
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(LocalDSColors.current.borderSubtle),
        )

        // Input container: summary + description in a single rounded box
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LocalDSColors.current.surfaceInput, RoundedCornerShape(DSRadius.md))
                .then(
                    Modifier.run {
                        // Stroke via outline drawn on top
                        this
                    },
                ),
        ) {
            // Summary row with AI sparkle button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = DSSpacing.sm, end = DSSpacing.xs, top = DSSpacing.xs),
                verticalAlignment = Alignment.Top,
            ) {
                OutlinedTextField(
                    value = summary,
                    onValueChange = { value ->
                        container.gitSidebarViewModel.updateCommitSummary(projectId, value)
                    },
                    placeholder = {
                        Text(
                            "Commit summary",
                            style = DSFont.commitInput,
                            color = LocalDSColors.current.textMuted,
                        )
                    },
                    textStyle = DSFont.commitInput.copy(color = LocalDSColors.current.textPrimary),
                    maxLines = 3,
                    minLines = 1,
                    enabled = !isCommitting,
                    shape = RoundedCornerShape(DSRadius.sm),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LocalDSColors.current.borderFocus,
                        unfocusedBorderColor = LocalDSColors.current.borderDefault,
                        cursorColor = LocalDSColors.current.accentPrimary,
                        focusedContainerColor = LocalDSColors.current.surfaceInput,
                        unfocusedContainerColor = LocalDSColors.current.surfaceInput,
                        disabledContainerColor = LocalDSColors.current.surfaceInput,
                        disabledBorderColor = LocalDSColors.current.borderDefault,
                        disabledTextColor = LocalDSColors.current.textDisabled,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = DSLayout.commitInputMinHeight),
                )

                Spacer(Modifier.width(DSSpacing.xs))

                // AI generate button
                IconButton(
                    onClick = {
                        container.gitSidebarViewModel.generateAICommitMessage(projectId, projectPath)
                    },
                    enabled = !isGenerating && !isCommitting,
                    modifier = Modifier
                        .size(DSLayout.sidebarActionButtonSize + DSSpacing.xs)
                        .padding(top = DSSpacing.xs),
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = LocalDSColors.current.accentPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "Generate commit message with AI",
                            tint = if (!isCommitting) LocalDSColors.current.accentPrimary else LocalDSColors.current.textMuted,
                            modifier = Modifier.size(DSFont.iconBase.value.dp),
                        )
                    }
                }
            }

            // Character counter — only visible when the summary is non-empty
            if (summary.isNotEmpty()) {
                Text(
                    text = "$charCount/$COMMIT_SUMMARY_LIMIT",
                    style = DSFont.sidebarItemSmall,
                    color = if (isOverLimit) LocalDSColors.current.gitDeleted else LocalDSColors.current.textMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = DSSpacing.xl, bottom = DSSpacing.xxs),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }

            // Divider between summary and description
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(LocalDSColors.current.borderSubtle),
            )

            // Description field
            OutlinedTextField(
                value = description,
                onValueChange = { value ->
                    container.gitSidebarViewModel.updateCommitDescription(projectId, value)
                },
                placeholder = {
                    Text(
                        "Description (optional)",
                        style = DSFont.sidebarItemSmall,
                        color = LocalDSColors.current.textMuted,
                    )
                },
                textStyle = DSFont.sidebarItemSmall.copy(color = LocalDSColors.current.textSecondary),
                maxLines = 5,
                minLines = 1,
                enabled = !isCommitting,
                shape = RoundedCornerShape(DSRadius.sm),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LocalDSColors.current.borderFocus,
                    unfocusedBorderColor = LocalDSColors.current.borderDefault,
                    cursorColor = LocalDSColors.current.accentPrimary,
                    focusedContainerColor = LocalDSColors.current.surfaceInput,
                    unfocusedContainerColor = LocalDSColors.current.surfaceInput,
                    disabledContainerColor = LocalDSColors.current.surfaceInput,
                    disabledBorderColor = LocalDSColors.current.borderDefault,
                    disabledTextColor = LocalDSColors.current.textDisabled,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DSSpacing.sm, vertical = DSSpacing.xs)
                    .heightIn(
                        min = DSLayout.commitInputMinHeight,
                        max = DSLayout.commitInputMaxHeight,
                    ),
            )
        }

        // Inline error message
        if (commitError != null) {
            Text(
                text = commitError,
                style = DSFont.sidebarItemSmall,
                color = LocalDSColors.current.gitDeleted,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Commit button (full width)
        Button(
            onClick = {
                container.gitSidebarViewModel.performCommit(projectId, projectPath)
            },
            enabled = canCommit,
            shape = RoundedCornerShape(DSRadius.md),
            colors = ButtonDefaults.buttonColors(
                containerColor = LocalDSColors.current.buttonPrimaryBg,
                contentColor = LocalDSColors.current.buttonPrimaryText,
                disabledContainerColor = LocalDSColors.current.surfaceOverlay,
                disabledContentColor = LocalDSColors.current.textDisabled,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(DSLayout.gitButtonHeight),
        ) {
            if (isCommitting) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = LocalDSColors.current.buttonPrimaryText,
                        strokeWidth = 2.dp,
                    )
                    Text("Committing...", style = DSFont.buttonLabel)
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs),
                ) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = null,
                        modifier = Modifier.size(DSFont.iconBase.value.dp),
                    )
                    Text("Commit All Changes", style = DSFont.buttonLabel)
                }
            }
        }
    }
}

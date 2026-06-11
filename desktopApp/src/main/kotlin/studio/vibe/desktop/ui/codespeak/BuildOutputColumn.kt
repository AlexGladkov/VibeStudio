@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui.codespeak

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.uuid.Uuid
import studio.vibe.desktop.ui.theme.DSColors
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSLayout
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.shared.feature.codespeak.domain.model.CodeSpeakCommand
import studio.vibe.shared.feature.filetree.domain.GeneratedFile
import studio.vibe.shared.feature.codespeak.domain.model.SpecFile
import studio.vibe.shared.feature.codespeak.domain.model.SpecStats
import studio.vibe.shared.feature.codespeak.presentation.SpecBuildPanelState

@Composable
internal fun BuildOutputColumn(
    buildState: SpecBuildPanelState,
    activeProjectId: Uuid?,
    selectedSpec: SpecFile?,
    generatedFiles: List<GeneratedFile>,
    onSelectCommand: (CodeSpeakCommand) -> Unit,
    onUpdateTaskName: (String) -> Unit,
    onUpdateChangeMessage: (String) -> Unit,
    onRun: (Uuid) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.background(LocalDSColors.current.surfaceRaised)) {
        BuildHeader(
            command = buildState.selectedCommand,
            taskName = buildState.taskName,
            changeMessage = buildState.changeMessage,
            isRunning = buildState.isRunning,
            exitStats = buildState.lastStats,
            onSelectCommand = onSelectCommand,
            onUpdateTaskName = onUpdateTaskName,
            onUpdateChangeMessage = onUpdateChangeMessage,
            onRun = { activeProjectId?.let { onRun(it) } },
            onStop = onStop,
            canRun = activeProjectId != null,
        )

        HorizontalDivider(color = LocalDSColors.current.borderSubtle, thickness = 1.dp)

        val outputLines = remember(buildState.output) {
            buildState.output.lines().filter { it.isNotEmpty() }
        }

        if (outputLines.isEmpty() && !buildState.isRunning) {
            BuildEmptyState(command = buildState.selectedCommand)
        } else {
            BuildOutputLines(lines = outputLines, isRunning = buildState.isRunning)
        }

        if (generatedFiles.isNotEmpty()) {
            HorizontalDivider(color = LocalDSColors.current.borderSubtle, thickness = 1.dp)
            GeneratedFilesSection(files = generatedFiles)
        }
    }
}

@Composable
private fun BuildHeader(
    command: CodeSpeakCommand,
    taskName: String,
    changeMessage: String,
    isRunning: Boolean,
    exitStats: SpecStats?,
    onSelectCommand: (CodeSpeakCommand) -> Unit,
    onUpdateTaskName: (String) -> Unit,
    onUpdateChangeMessage: (String) -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit,
    canRun: Boolean,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(DSLayout.gitSectionHeaderHeight)
                .padding(horizontal = DSSpacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(LocalDSColors.current.agentCodeSpeak, CircleShape),
            )
            Spacer(Modifier.width(DSSpacing.xs))

            CommandSelectorDropdown(
                selected = command,
                isRunning = isRunning,
                onSelect = onSelectCommand,
            )

            Spacer(Modifier.weight(1f))

            if (command.supportsStatsParsing && exitStats != null) {
                ExitStatsBadge(stats = exitStats)
                Spacer(Modifier.width(DSSpacing.xs))
            }

            PlayStopButton(
                isRunning = isRunning,
                canRun = canRun,
                onRun = onRun,
                onStop = onStop,
            )
        }

        if (command.requiresInput) {
            CommandInputRow(
                command = command,
                taskName = taskName,
                changeMessage = changeMessage,
                isRunning = isRunning,
                onUpdateTaskName = onUpdateTaskName,
                onUpdateChangeMessage = onUpdateChangeMessage,
            )
        }
    }
}

@Composable
private fun ExitStatsBadge(stats: SpecStats) {
    val badge = if (stats.allPassing) "PASS" else "FAIL"
    val badgeColor = if (stats.allPassing) LocalDSColors.current.gitAdded else LocalDSColors.current.gitDeleted
    val badgeBg = if (stats.allPassing) LocalDSColors.current.diffAddedBg else LocalDSColors.current.diffDeletedBg

    Text(
        text = badge,
        style = DSFont.badgeSmall,
        color = badgeColor,
        modifier = Modifier
            .background(badgeBg, RoundedCornerShape(DSRadius.sm))
            .padding(horizontal = DSSpacing.xs, vertical = DSSpacing.xxs),
    )
    Spacer(Modifier.width(DSSpacing.xs))
    Text(
        text = "${stats.passing}/${stats.total}",
        style = DSFont.sidebarItemSmall,
        color = if (stats.allPassing) LocalDSColors.current.gitAdded else LocalDSColors.current.gitModified,
    )
}

@Composable
private fun CommandSelectorDropdown(
    selected: CodeSpeakCommand,
    isRunning: Boolean,
    onSelect: (CodeSpeakCommand) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(DSRadius.sm))
                .clickable(enabled = !isRunning) { expanded = true }
                .padding(horizontal = DSSpacing.xs, vertical = DSSpacing.xxs),
        ) {
            Text(
                text = selected.displayName,
                style = DSFont.sidebarSection,
                color = if (isRunning) LocalDSColors.current.textSecondary else LocalDSColors.current.textPrimary,
            )
            Spacer(Modifier.width(DSSpacing.xxs))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Select command",
                tint = LocalDSColors.current.textMuted,
                modifier = Modifier.size(12.dp),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            CodeSpeakCommand.entries.forEach { cmd ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = cmd.displayName,
                            style = DSFont.sidebarItem,
                            color = if (cmd == selected) LocalDSColors.current.accentPrimary else LocalDSColors.current.textPrimary,
                        )
                    },
                    onClick = {
                        onSelect(cmd)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PlayStopButton(
    isRunning: Boolean,
    canRun: Boolean,
    onRun: () -> Unit,
    onStop: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "runPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    val iconColor = when {
        isRunning -> LocalDSColors.current.actionStop.copy(alpha = pulseAlpha)
        !canRun -> LocalDSColors.current.textDisabled
        else -> LocalDSColors.current.actionRun
    }

    IconButton(
        onClick = if (isRunning) onStop else onRun,
        enabled = canRun || isRunning,
        modifier = Modifier.size(DSLayout.gitSectionHeaderHeight),
    ) {
        Icon(
            imageVector = if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = if (isRunning) "Stop" else "Run",
            tint = iconColor,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun CommandInputRow(
    command: CodeSpeakCommand,
    taskName: String,
    changeMessage: String,
    isRunning: Boolean,
    onUpdateTaskName: (String) -> Unit,
    onUpdateChangeMessage: (String) -> Unit,
) {
    val value = if (command == CodeSpeakCommand.TASK) taskName else changeMessage
    val onValueChange: (String) -> Unit =
        if (command == CodeSpeakCommand.TASK) onUpdateTaskName else onUpdateChangeMessage

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(DSLayout.gitButtonHeight)
            .padding(horizontal = DSSpacing.md),
    ) {
        Text(
            text = command.inputLabel,
            style = DSFont.sidebarItemSmall,
            color = LocalDSColors.current.textSecondary,
            modifier = Modifier.width(44.dp),
        )
        Spacer(Modifier.width(DSSpacing.xs))
        BasicTextField(
            value = value,
            onValueChange = { if (!isRunning) onValueChange(it) },
            enabled = !isRunning,
            textStyle = DSFont.sidebarItem.copy(color = LocalDSColors.current.textPrimary),
            cursorBrush = SolidColor(LocalDSColors.current.accentPrimary),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .background(LocalDSColors.current.surfaceInput, RoundedCornerShape(DSRadius.sm))
                .padding(horizontal = DSSpacing.xs, vertical = DSSpacing.xxs),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = command.inputPlaceholder,
                            style = DSFont.sidebarItem,
                            color = LocalDSColors.current.textMuted,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun BuildOutputLines(
    lines: List<String>,
    isRunning: Boolean,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.lastIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = DSSpacing.xs),
    ) {
        itemsIndexed(items = lines) { _, line ->
            SelectionContainer {
                Text(
                    text = line,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = buildLineColor(line, LocalDSColors.current),
                        lineHeight = 16.sp,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DSSpacing.md, vertical = 1.dp),
                )
            }
        }

        if (isRunning) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(
                        horizontal = DSSpacing.md,
                        vertical = DSSpacing.xs,
                    ),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(8.dp),
                        color = LocalDSColors.current.indicatorRunning,
                        strokeWidth = 1.5.dp,
                    )
                    Spacer(Modifier.width(DSSpacing.xs))
                    Text(
                        text = "running…",
                        style = DSFont.monoSmall,
                        color = LocalDSColors.current.textMuted,
                    )
                }
            }
        }
    }
}

private fun buildLineColor(line: String, colors: DSColors): Color {
    val lower = line.lowercase()
    return when {
        lower.contains("error") || lower.contains("fail") || line.startsWith("⚠") ->
            colors.gitDeleted
        lower.contains("pass") || lower.contains("ok") ||
            line.contains("✓") || line.contains("✔") ->
            colors.gitAdded
        lower.contains("warn") ->
            colors.gitModified
        else -> colors.textSecondary
    }
}

@Composable
private fun BuildEmptyState(command: CodeSpeakCommand) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = LocalDSColors.current.textMuted,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(DSSpacing.sm))
            Text(
                text = "Run CodeSpeak to see output",
                style = DSFont.sidebarItem,
                color = LocalDSColors.current.textMuted,
            )
            Spacer(Modifier.height(DSSpacing.xxs))
            Text(
                text = "Press ▶ to ${command.displayName.lowercase()}",
                style = DSFont.sidebarItemSmall,
                color = LocalDSColors.current.textDisabled,
            )
        }
    }
}

@Composable
internal fun GeneratedFilesSection(files: List<GeneratedFile>) {
    var expanded by remember { mutableStateOf(true) }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(DSLayout.gitSectionHeaderHeight)
                .clickable { expanded = !expanded }
                .padding(start = DSSpacing.sm, end = DSSpacing.md),
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "Collapse generated files" else "Expand generated files",
                tint = LocalDSColors.current.textMuted,
                modifier = Modifier.size(DSLayout.chevronFrameWidth),
            )
            Spacer(Modifier.width(DSSpacing.xs))
            Text(
                text = "GENERATED",
                style = DSFont.sidebarSection,
                color = LocalDSColors.current.textSecondary,
            )
            Spacer(Modifier.width(DSSpacing.xs))
            Text(
                text = "${files.size}",
                style = DSFont.sidebarItemSmall,
                color = LocalDSColors.current.textMuted,
            )
        }

        if (expanded) {
            files.forEach { file ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DSLayout.gitFileRowHeight)
                        .padding(horizontal = DSSpacing.sm),
                ) {
                    Spacer(Modifier.width(DSLayout.chevronFrameWidth))
                    Spacer(Modifier.width(DSSpacing.xs))
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = LocalDSColors.current.gitAdded,
                        modifier = Modifier.size(10.dp),
                    )
                    Spacer(Modifier.width(DSSpacing.xs))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.name,
                            style = DSFont.sidebarItem,
                            color = LocalDSColors.current.textSecondary,
                            maxLines = 1,
                        )
                        if (file.specName.isNotEmpty()) {
                            Text(
                                text = file.specName,
                                style = DSFont.sidebarItemSmall,
                                color = LocalDSColors.current.textMuted,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

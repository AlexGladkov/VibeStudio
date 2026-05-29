@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer // used in BuildOutputLines
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSLayout
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.shared.contract.LineContext
import studio.vibe.shared.contract.SyntaxTokenKind
import studio.vibe.shared.model.CodeSpeakCommand
import studio.vibe.shared.model.GeneratedFile
import studio.vibe.shared.model.Project
import studio.vibe.shared.model.SpecFile
import studio.vibe.shared.model.SpecStats
import studio.vibe.shared.model.SpecStatus
import studio.vibe.shared.service.syntax.CodeSpeakSyntaxParser
import studio.vibe.shared.viewmodel.CodeSpeakModeViewModel
import studio.vibe.shared.viewmodel.SpecBuildPanelState
import studio.vibe.shared.viewmodel.SpecBuildPanelViewModel
import java.awt.Cursor
import kotlin.uuid.Uuid

// ── CodeSpeakModeView ────────────────────────────────────────────────────────
//
// 3-column layout:
//   LEFT   — Specs list (resizable, 220dp default)
//   CENTER — Spec editor (read-only code viewer, weight 1f)
//   RIGHT  — Build output panel (resizable, 280dp default)
//
// Mirrors CodeSpeakModeView.swift layout with Compose Desktop idioms.

/**
 * Full-window CodeSpeak mode: spec list | editor | build output.
 *
 * Creates [CodeSpeakModeViewModel] and [SpecBuildPanelViewModel] bound to the
 * container's coroutine scope. All cross-panel state is hoisted here.
 *
 * @param container Application-scoped DI container.
 * @param modifier  Modifier applied to the outermost [Row].
 */
@Composable
public fun CodeSpeakModeView(
    container: DesktopServiceContainer,
    modifier: Modifier = Modifier,
) {
    // ── ViewModels ──────────────────────────────────────────────────────────
    val modeVm = remember {
        CodeSpeakModeViewModel(
            persistenceStore = container.persistenceStore,
            projectManaging = container.projectStore,
            scope = container.scope,
        )
    }
    val buildVm = remember {
        SpecBuildPanelViewModel(
            processRunner = container.processRunner,
            projectManaging = container.projectStore,
            scope = container.scope,
        )
    }

    val modeState by modeVm.state.collectAsState()
    val buildState by buildVm.state.collectAsState()

    // ── Active project ──────────────────────────────────────────────────────
    val activeProjectId by container.projectStore.activeProjectId.collectAsState()
    val projects by container.projectStore.projects.collectAsState()
    val activeProject by remember(activeProjectId, projects) {
        derivedStateOf { activeProjectId?.let { id -> projects.find { it.id == id } } }
    }

    // Load specs when the active project changes
    LaunchedEffect(activeProjectId) {
        activeProjectId?.let { modeVm.loadSpecs(it) }
    }

    // Scan for generated files when the active project changes
    LaunchedEffect(activeProjectId) {
        activeProjectId?.let { modeVm.scanGeneratedFiles(it) }
    }

    // ── Panel widths (user-resizable) ───────────────────────────────────────
    var specsWidth by remember { mutableStateOf(220.dp) }
    var buildWidth by remember { mutableStateOf(280.dp) }
    val density = LocalDensity.current

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(DSColor.surfaceBase)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.isMetaPressed) {
                    when (event.key) {
                        Key.S -> {
                            modeVm.saveCurrentSpec()
                            true
                        }
                        Key.Enter -> {
                            activeProjectId?.let { buildVm.runCommand(it, modeState.selectedSpec) }
                            true
                        }
                        else -> false
                    }
                } else false
            },
    ) {
        // ── LEFT: Specs list ──────────────────────────────────────────────
        SpecsListColumn(
            container = container,
            specs = modeState.specs,
            selectedSpecId = modeState.selectedSpec?.id,
            isLoading = modeState.isLoading,
            activeProjectId = activeProjectId,
            onSelectSpec = { modeVm.selectSpec(it) },
            onRefresh = { activeProjectId?.let { modeVm.loadSpecs(it) } },
            onSpecsChanged = { activeProjectId?.let { modeVm.loadSpecs(it) } },
            modifier = Modifier
                .width(specsWidth)
                .fillMaxHeight(),
        )

        // Resize handle — specs ↔ editor
        CodeSpeakResizeHandle(
            onDrag = { delta ->
                val newWidth = specsWidth + with(density) { delta.toDp() }
                specsWidth = newWidth.coerceIn(160.dp, 380.dp)
            },
        )

        // ── CENTER: Editor ─────────────────────────────────────────────────
        EditorColumn(
            selectedSpec = modeState.selectedSpec,
            editorContent = modeState.editorContent,
            isEditorDirty = modeState.isEditorDirty,
            onContentChange = { modeVm.updateEditorContent(it) },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )

        // Resize handle — editor ↔ build output
        CodeSpeakResizeHandle(
            onDrag = { delta ->
                val newWidth = buildWidth - with(density) { delta.toDp() }
                buildWidth = newWidth.coerceIn(200.dp, 520.dp)
            },
        )

        // ── RIGHT: Build output ────────────────────────────────────────────
        BuildOutputColumn(
            buildState = buildState,
            activeProjectId = activeProjectId,
            selectedSpec = modeState.selectedSpec,
            generatedFiles = modeState.generatedFiles,
            onSelectCommand = { buildVm.selectCommand(it) },
            onUpdateTaskName = { buildVm.updateTaskName(it) },
            onUpdateChangeMessage = { buildVm.updateChangeMessage(it) },
            onRun = { projectId -> buildVm.runCommand(projectId, modeState.selectedSpec) },
            onStop = { buildVm.stopCommand() },
            modifier = Modifier
                .width(buildWidth)
                .fillMaxHeight(),
        )
    }
}

// ── Left column — Specs list ──────────────────────────────────────────────────

/**
 * Sealed dialog state for CodeSpeak left panel.
 * - [None]   — no dialog open.
 * - [Editor] — [SpecEditorSheet] open for a specific spec.
 * - [Wizard] — [SpecWizardSheet] open for creating a new spec.
 */
private sealed class SpecsPanelDialog {
    data object None : SpecsPanelDialog()
    data class Editor(val spec: SpecFile) : SpecsPanelDialog()
    data object Wizard : SpecsPanelDialog()
}

@Composable
private fun SpecsListColumn(
    container: DesktopServiceContainer,
    specs: List<SpecFile>,
    selectedSpecId: Uuid?,
    isLoading: Boolean,
    activeProjectId: Uuid?,
    onSelectSpec: (SpecFile) -> Unit,
    onRefresh: () -> Unit,
    onSpecsChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sectionExpanded by remember { mutableStateOf(true) }
    var activeDialog by remember { mutableStateOf<SpecsPanelDialog>(SpecsPanelDialog.None) }

    Column(modifier = modifier.background(DSColor.surfaceRaised)) {

        // Section header row
        SpecsSectionHeader(
            specs = specs,
            isExpanded = sectionExpanded,
            onToggleExpand = { sectionExpanded = !sectionExpanded },
            onRefresh = onRefresh,
        )

        HorizontalDivider(color = DSColor.borderSubtle, thickness = 1.dp)

        // List / loading / empty body
        Box(modifier = Modifier.weight(1f)) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = DSColor.accentPrimary,
                            strokeWidth = 2.dp,
                        )
                    }
                }

                sectionExpanded && specs.isEmpty() -> {
                    Text(
                        text = "No specs found",
                        style = DSFont.sidebarItemSmall,
                        color = DSColor.textMuted,
                        modifier = Modifier.padding(
                            start = DSSpacing.md + DSLayout.chevronFrameWidth,
                            top = DSSpacing.xs,
                        ),
                    )
                }

                sectionExpanded -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(
                            items = specs,
                            key = { _, spec -> spec.id.toString() },
                        ) { _, spec ->
                            SpecRow(
                                spec = spec,
                                isSelected = spec.id == selectedSpecId,
                                onClick = {
                                    onSelectSpec(spec)
                                    activeDialog = SpecsPanelDialog.Editor(spec)
                                },
                            )
                        }
                    }
                }
            }
        }

        // New Spec footer button
        HorizontalDivider(color = DSColor.borderSubtle, thickness = 1.dp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(DSLayout.gitButtonHeight)
                .clickable(enabled = activeProjectId != null) {
                    activeDialog = SpecsPanelDialog.Wizard
                }
                .padding(horizontal = DSSpacing.md),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "New Spec",
                tint = if (activeProjectId != null) DSColor.textSecondary else DSColor.textDisabled,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(DSSpacing.xs))
            Text(
                text = "New Spec",
                style = DSFont.buttonLabel,
                color = if (activeProjectId != null) DSColor.textSecondary else DSColor.textDisabled,
            )
        }
    }

    // ── Dialog overlays ──────────────────────────────────────────────────
    when (val dialog = activeDialog) {
        is SpecsPanelDialog.Editor -> {
            SpecEditorSheet(
                container = container,
                specFile = dialog.spec,
                onDismiss = { activeDialog = SpecsPanelDialog.None },
            )
        }
        is SpecsPanelDialog.Wizard -> {
            val projects by container.projectStore.projects.collectAsState()
            val project = remember(activeProjectId, projects) {
                activeProjectId?.let { id -> projects.find { it.id == id } }
            }
            if (project != null) {
                SpecWizardSheet(
                    container = container,
                    projectId = project.id,
                    projectPath = project.path,
                    onCreated = { onSpecsChanged() },
                    onDismiss = { activeDialog = SpecsPanelDialog.None },
                )
            } else {
                activeDialog = SpecsPanelDialog.None
            }
        }
        SpecsPanelDialog.None -> Unit
    }
}

@Composable
private fun SpecsSectionHeader(
    specs: List<SpecFile>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onRefresh: () -> Unit,
) {
    val passing = specs.count { it.status == SpecStatus.PASSING }
    val total = specs.size
    val allPassing = total > 0 && passing == total

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(DSLayout.gitSectionHeaderHeight)
            .clickable { onToggleExpand() }
            .padding(start = DSSpacing.sm, end = DSSpacing.md),
    ) {
        Icon(
            imageVector = if (isExpanded) {
                Icons.Filled.KeyboardArrowDown
            } else {
                Icons.Filled.KeyboardArrowRight
            },
            contentDescription = if (isExpanded) "Collapse specs" else "Expand specs",
            tint = DSColor.textMuted,
            modifier = Modifier.size(DSLayout.chevronFrameWidth),
        )
        Spacer(Modifier.width(DSSpacing.xs))
        Text(
            text = "SPECS",
            style = DSFont.sidebarSection,
            color = DSColor.textSecondary,
        )
        if (total > 0) {
            Spacer(Modifier.width(DSSpacing.xs))
            Text(
                text = "$passing/$total",
                style = DSFont.sidebarItemSmall,
                color = if (allPassing) DSColor.gitAdded else DSColor.gitModified,
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = onRefresh,
            modifier = Modifier.size(DSLayout.gitSectionHeaderHeight),
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Refresh specs",
                tint = DSColor.textMuted,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}

@Composable
private fun SpecRow(
    spec: SpecFile,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) DSColor.accentPrimary.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(durationMillis = 120),
        label = "specRowBg",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(DSLayout.gitFileRowHeight)
            .padding(horizontal = DSSpacing.sm)
            .clip(RoundedCornerShape(DSRadius.sm))
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = DSSpacing.xs),
    ) {
        // Align with the section chevron column
        Spacer(Modifier.width(DSLayout.chevronFrameWidth))
        Spacer(Modifier.width(DSSpacing.xs))

        // Status indicator dot
        Box(
            modifier = Modifier
                .size(DSLayout.indicatorSize)
                .background(color = specStatusColor(spec.status), shape = CircleShape),
        )
        Spacer(Modifier.width(DSSpacing.xs))

        Text(
            text = spec.name,
            style = DSFont.sidebarItem,
            color = if (isSelected) DSColor.textPrimary else DSColor.textSecondary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )

        // Badge: checkmark / x / nothing
        when (spec.status) {
            SpecStatus.PASSING -> Text(
                text = "\u2713",
                style = DSFont.statusBadge,
                color = DSColor.gitAdded,
            )
            SpecStatus.FAILING -> Text(
                text = "\u00D7",
                style = DSFont.statusBadge,
                color = DSColor.gitDeleted,
            )
            SpecStatus.UNKNOWN -> Unit
        }
        Spacer(Modifier.width(DSSpacing.xs))
    }
}

private fun specStatusColor(status: SpecStatus): Color = when (status) {
    SpecStatus.PASSING -> DSColor.gitAdded
    SpecStatus.FAILING -> DSColor.gitDeleted
    SpecStatus.UNKNOWN -> DSColor.indicatorIdle
}

// ── Center column — Editor ────────────────────────────────────────────────────

@Composable
private fun EditorColumn(
    selectedSpec: SpecFile?,
    editorContent: String,
    isEditorDirty: Boolean,
    onContentChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.background(DSColor.surfaceBase)) {
        if (selectedSpec != null) {
            // Breadcrumb — top of center column, left-aligned per CLAUDE.md invariant
            EditorBreadcrumb(spec = selectedSpec, isEditorDirty = isEditorDirty)
            HorizontalDivider(color = DSColor.borderSubtle, thickness = 1.dp)

            // Editable syntax-highlighted content (monospace scroll view)
            val scrollState = rememberScrollState()
            BasicTextField(
                value = editorContent,
                onValueChange = onContentChange,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = DSColor.textPrimary,
                    lineHeight = 18.sp,
                ),
                cursorBrush = SolidColor(DSColor.accentPrimary),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
                visualTransformation = { text ->
                    val annotated = highlightCodeSpeak(text.text)
                    TransformedText(annotated, OffsetMapping.Identity)
                },
            )
        } else {
            EditorEmptyState()
        }
    }
}

/**
 * "Projects > parentDir > specName" breadcrumb displayed at the top of the
 * center column when a spec is selected.
 *
 * Placement: ВЕРХ ЦЕНТРАЛЬНОЙ ПАНЕЛИ, по ЛЕВОМУ КРАЮ — per CLAUDE.md invariant.
 */
@Composable
private fun EditorBreadcrumb(spec: SpecFile, isEditorDirty: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(DSLayout.gitSectionHeaderHeight)
            .padding(horizontal = DSSpacing.md),
    ) {
        Text(text = "Projects", style = DSFont.sidebarItemSmall, color = DSColor.textMuted)
        BreadcrumbSeparator()
        Text(
            text = spec.path.parent.name.ifEmpty { "project" },
            style = DSFont.sidebarItemSmall,
            color = DSColor.textMuted,
        )
        BreadcrumbSeparator()
        Text(text = spec.name, style = DSFont.sidebarItemSmall, color = DSColor.textPrimary)
        if (isEditorDirty) {
            Spacer(Modifier.width(DSSpacing.xs))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(DSColor.gitModified, CircleShape),
            )
        }
    }
}

@Composable
private fun BreadcrumbSeparator() {
    Text(text = " \u203A ", style = DSFont.sidebarItemSmall, color = DSColor.textMuted)
}

@Composable
private fun EditorEmptyState() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.TextSnippet,
                contentDescription = null,
                tint = DSColor.textMuted,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(DSSpacing.sm))
            Text(
                text = "Select a spec to view",
                style = DSFont.sidebarItem,
                color = DSColor.textMuted,
            )
        }
    }
}

// ── Right column — Build output ───────────────────────────────────────────────

@Composable
private fun BuildOutputColumn(
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
    Column(modifier = modifier.background(DSColor.surfaceRaised)) {
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

        HorizontalDivider(color = DSColor.borderSubtle, thickness = 1.dp)

        val outputLines = remember(buildState.output) {
            buildState.output.lines().filter { it.isNotEmpty() }
        }

        if (outputLines.isEmpty() && !buildState.isRunning) {
            BuildEmptyState(command = buildState.selectedCommand)
        } else {
            BuildOutputLines(lines = outputLines, isRunning = buildState.isRunning)
        }

        if (generatedFiles.isNotEmpty()) {
            HorizontalDivider(color = DSColor.borderSubtle, thickness = 1.dp)
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
        // Primary row: accent dot + command selector + stats badge + play/stop
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(DSLayout.gitSectionHeaderHeight)
                .padding(horizontal = DSSpacing.md),
        ) {
            // CodeSpeak brand accent
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(DSColor.agentCodeSpeak, CircleShape),
            )
            Spacer(Modifier.width(DSSpacing.xs))

            CommandSelectorDropdown(
                selected = command,
                isRunning = isRunning,
                onSelect = onSelectCommand,
            )

            Spacer(Modifier.weight(1f))

            // PASS / FAIL badge + stats — only for BUILD command
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

        // Optional input row for TASK / CHANGE commands
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
    val badgeColor = if (stats.allPassing) DSColor.gitAdded else DSColor.gitDeleted
    val badgeBg = if (stats.allPassing) DSColor.diffAddedBg else DSColor.diffDeletedBg

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
        color = if (stats.allPassing) DSColor.gitAdded else DSColor.gitModified,
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
                color = if (isRunning) DSColor.textSecondary else DSColor.textPrimary,
            )
            Spacer(Modifier.width(DSSpacing.xxs))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Select command",
                tint = DSColor.textMuted,
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
                            color = if (cmd == selected) DSColor.accentPrimary else DSColor.textPrimary,
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
    // Pulsing alpha animation while running
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
        isRunning -> DSColor.actionStop.copy(alpha = pulseAlpha)
        !canRun -> DSColor.textDisabled
        else -> DSColor.actionRun
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
            color = DSColor.textSecondary,
            modifier = Modifier.width(44.dp),
        )
        Spacer(Modifier.width(DSSpacing.xs))
        BasicTextField(
            value = value,
            onValueChange = { if (!isRunning) onValueChange(it) },
            enabled = !isRunning,
            textStyle = DSFont.sidebarItem.copy(color = DSColor.textPrimary),
            cursorBrush = SolidColor(DSColor.accentPrimary),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .background(DSColor.surfaceInput, RoundedCornerShape(DSRadius.sm))
                .padding(horizontal = DSSpacing.xs, vertical = DSSpacing.xxs),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = command.inputPlaceholder,
                            style = DSFont.sidebarItem,
                            color = DSColor.textMuted,
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

    // Auto-scroll to latest line
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
                        color = buildLineColor(line),
                        lineHeight = 16.sp,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DSSpacing.md, vertical = 1.dp),
                )
            }
        }

        // Running indicator at tail
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
                        color = DSColor.indicatorRunning,
                        strokeWidth = 1.5.dp,
                    )
                    Spacer(Modifier.width(DSSpacing.xs))
                    Text(
                        text = "running\u2026",
                        style = DSFont.monoSmall,
                        color = DSColor.textMuted,
                    )
                }
            }
        }
    }
}

/** Returns the appropriate terminal-line color following Swift source rules. */
private fun buildLineColor(line: String): Color {
    val lower = line.lowercase()
    return when {
        lower.contains("error") || lower.contains("fail") || line.startsWith("\u26A0") ->
            DSColor.gitDeleted
        lower.contains("pass") || lower.contains("ok") ||
            line.contains("\u2713") || line.contains("\u2714") ->
            DSColor.gitAdded
        lower.contains("warn") ->
            DSColor.gitModified
        else -> DSColor.textSecondary
    }
}

@Composable
private fun BuildEmptyState(command: CodeSpeakCommand) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = DSColor.textMuted,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(DSSpacing.sm))
            Text(
                text = "Run CodeSpeak to see output",
                style = DSFont.sidebarItem,
                color = DSColor.textMuted,
            )
            Spacer(Modifier.height(DSSpacing.xxs))
            Text(
                text = "Press \u25B6 to ${command.displayName.lowercase()}",
                style = DSFont.sidebarItemSmall,
                color = DSColor.textDisabled,
            )
        }
    }
}

// ── Generated files section ───────────────────────────────────────────────────

@Composable
private fun GeneratedFilesSection(files: List<GeneratedFile>) {
    var expanded by remember { mutableStateOf(true) }

    Column {
        // Section header
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
                tint = DSColor.textMuted,
                modifier = Modifier.size(DSLayout.chevronFrameWidth),
            )
            Spacer(Modifier.width(DSSpacing.xs))
            Text(
                text = "GENERATED",
                style = DSFont.sidebarSection,
                color = DSColor.textSecondary,
            )
            Spacer(Modifier.width(DSSpacing.xs))
            Text(
                text = "${files.size}",
                style = DSFont.sidebarItemSmall,
                color = DSColor.textMuted,
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
                        tint = DSColor.gitAdded,
                        modifier = Modifier.size(10.dp),
                    )
                    Spacer(Modifier.width(DSSpacing.xs))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.name,
                            style = DSFont.sidebarItem,
                            color = DSColor.textSecondary,
                            maxLines = 1,
                        )
                        if (file.specName.isNotEmpty()) {
                            Text(
                                text = file.specName,
                                style = DSFont.sidebarItemSmall,
                                color = DSColor.textMuted,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Syntax highlighting helpers ───────────────────────────────────────────────

private fun highlightCodeSpeak(content: String): AnnotatedString {
    val parser = CodeSpeakSyntaxParser()
    return buildAnnotatedString {
        append(content)
        var context = LineContext.INITIAL
        var offset = 0
        for (line in content.lines()) {
            val lineEnd = offset + line.length
            val (tokens, nextContext) = parser.parseLine(line, offset, lineEnd, context)
            for (token in tokens) {
                val style = tokenStyle(token.kind)
                if (style != null) {
                    addStyle(style, token.startOffset, token.endOffset.coerceAtMost(length))
                }
            }
            context = nextContext
            offset = lineEnd + 1 // +1 for newline
        }
    }
}

private fun tokenStyle(kind: SyntaxTokenKind): SpanStyle? = when (kind) {
    SyntaxTokenKind.HEADING -> SpanStyle(color = DSColor.accentPrimary, fontWeight = FontWeight.Bold)
    SyntaxTokenKind.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
    SyntaxTokenKind.ITALIC -> SpanStyle(fontWeight = FontWeight.Light)
    SyntaxTokenKind.INLINE_CODE -> SpanStyle(color = DSColor.gitModified, fontFamily = FontFamily.Monospace)
    SyntaxTokenKind.CODE_BLOCK_FENCE -> SpanStyle(color = DSColor.textMuted)
    SyntaxTokenKind.CODE_BLOCK_BODY -> SpanStyle(color = DSColor.textSecondary)
    SyntaxTokenKind.COMMENT -> SpanStyle(color = DSColor.textMuted)
    SyntaxTokenKind.FRONTMATTER_DELIMITER -> SpanStyle(color = DSColor.textMuted)
    SyntaxTokenKind.FRONTMATTER_KEY -> SpanStyle(color = DSColor.accentPrimary)
    SyntaxTokenKind.FRONTMATTER_VALUE -> SpanStyle(color = DSColor.gitAdded)
    SyntaxTokenKind.CS_DIRECTIVE -> SpanStyle(color = DSColor.agentCodeSpeak, fontWeight = FontWeight.Bold)
    SyntaxTokenKind.CS_FILE_REF -> SpanStyle(color = DSColor.accentSecondary)
    SyntaxTokenKind.LINK -> SpanStyle(color = DSColor.accentPrimary)
    SyntaxTokenKind.LINK_URL -> SpanStyle(color = DSColor.accentSecondary)
    SyntaxTokenKind.BLOCKQUOTE -> SpanStyle(color = DSColor.textSecondary)
    SyntaxTokenKind.LIST_MARKER -> SpanStyle(color = DSColor.accentPrimary)
    SyntaxTokenKind.HORIZONTAL_RULE -> SpanStyle(color = DSColor.textMuted)
    SyntaxTokenKind.PLAIN -> null
    else -> null
}

// ── Resize handle ─────────────────────────────────────────────────────────────

/**
 * Draggable 5dp-wide vertical resize divider.
 * Uses the same pattern as [ResizeHandle] in RootView.kt.
 */
@Composable
private fun CodeSpeakResizeHandle(onDrag: (Float) -> Unit) {
    Box(
        modifier = Modifier
            .width(5.dp)
            .fillMaxHeight()
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    onDrag(dragAmount.x)
                }
            }
            .background(DSColor.borderDefault),
    )
}

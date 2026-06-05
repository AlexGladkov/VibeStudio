@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui.codespeak

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import studio.vibe.shared.contract.PersistenceStore
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.desktop.ui.SpecEditorSheet
import studio.vibe.desktop.ui.SpecWizardSheet
import studio.vibe.desktop.ui.theme.DSColors
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSLayout
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.shared.coordinator.AppNavigationCoordinator
import studio.vibe.shared.model.SpecFile
import studio.vibe.shared.model.SpecStatus

private sealed class SpecsPanelDialog {
    data object None : SpecsPanelDialog()
    data class Editor(val spec: SpecFile) : SpecsPanelDialog()
    data object Wizard : SpecsPanelDialog()
}

@Composable
internal fun SpecsListColumn(
    persistenceStore: PersistenceStore,
    projectStore: ProjectManaging,
    coroutineScope: CoroutineScope,
    specs: List<SpecFile>,
    generatedFiles: List<studio.vibe.shared.model.GeneratedFile>,
    selectedSpecId: Uuid?,
    selectedGeneratedFilePath: String?,
    isLoading: Boolean,
    showFailingOnly: Boolean,
    activeProjectId: Uuid?,
    onSelectSpec: (SpecFile) -> Unit,
    onSelectGeneratedFile: (studio.vibe.shared.model.GeneratedFile) -> Unit,
    onRefresh: () -> Unit,
    onSpecsChanged: () -> Unit,
    navigationCoordinator: AppNavigationCoordinator? = null,
    modifier: Modifier = Modifier,
) {
    var sectionExpanded by remember { mutableStateOf(true) }
    var generatedExpanded by remember { mutableStateOf(true) }
    var activeDialog by remember { mutableStateOf<SpecsPanelDialog>(SpecsPanelDialog.None) }

    val visibleSpecs = remember(specs, showFailingOnly) {
        if (showFailingOnly) {
            val failing = specs.filter { it.status == studio.vibe.shared.model.SpecStatus.FAILING }
            failing.ifEmpty { specs }
        } else {
            specs
        }
    }

    Column(
        modifier = modifier
            .background(LocalDSColors.current.surfaceRaised)
            .onGloballyPositioned { coords ->
                navigationCoordinator?.setSpecsColumnWidth(coords.size.width.toFloat())
            },
    ) {

        SpecsSectionHeader(
            specs = specs,
            isExpanded = sectionExpanded,
            onToggleExpand = { sectionExpanded = !sectionExpanded },
            onRefresh = onRefresh,
        )

        HorizontalDivider(color = LocalDSColors.current.borderSubtle, thickness = 1.dp)

        Box(modifier = Modifier.weight(1f)) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = LocalDSColors.current.accentPrimary,
                            strokeWidth = 2.dp,
                        )
                    }
                }

                sectionExpanded && visibleSpecs.isEmpty() -> {
                    Text(
                        text = "No specs found",
                        style = DSFont.sidebarItemSmall,
                        color = LocalDSColors.current.textMuted,
                        modifier = Modifier.padding(
                            start = DSSpacing.md + DSLayout.chevronFrameWidth,
                            top = DSSpacing.xs,
                        ),
                    )
                }

                sectionExpanded -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(
                            items = visibleSpecs,
                            key = { _, spec -> spec.id.toString() },
                        ) { _, spec ->
                            SpecListRow(
                                spec = spec,
                                isSelected = spec.id == selectedSpecId,
                                onClick = {
                                    onSelectSpec(spec)
                                    activeDialog = SpecsPanelDialog.Editor(spec)
                                },
                            )
                        }

                        if (generatedFiles.isNotEmpty()) {
                            item(key = "generated-header") {
                                HorizontalDivider(color = LocalDSColors.current.borderSubtle, thickness = 1.dp)
                                GeneratedSectionHeader(
                                    count = generatedFiles.size,
                                    isExpanded = generatedExpanded,
                                    onToggleExpand = { generatedExpanded = !generatedExpanded },
                                    onRefresh = onRefresh,
                                )
                            }
                        }

                        if (generatedFiles.isNotEmpty() && generatedExpanded) {
                            itemsIndexed(
                                items = generatedFiles,
                                key = { _, f -> "gen:${f.path.path}" },
                            ) { _, file ->
                                GeneratedFileRow(
                                    file = file,
                                    isSelected = file.path.path == selectedGeneratedFilePath,
                                    onClick = { onSelectGeneratedFile(file) },
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = LocalDSColors.current.borderSubtle, thickness = 1.dp)
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
                tint = if (activeProjectId != null) LocalDSColors.current.textSecondary else LocalDSColors.current.textDisabled,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(DSSpacing.xs))
            Text(
                text = "New Spec",
                style = DSFont.buttonLabel,
                color = if (activeProjectId != null) LocalDSColors.current.textSecondary else LocalDSColors.current.textDisabled,
            )
        }
    }

    when (val dialog = activeDialog) {
        is SpecsPanelDialog.Editor -> {
            SpecEditorSheet(
                persistenceStore = persistenceStore,
                coroutineScope = coroutineScope,
                specFile = dialog.spec,
                onDismiss = { activeDialog = SpecsPanelDialog.None },
            )
        }
        is SpecsPanelDialog.Wizard -> {
            val projects by projectStore.projects.collectAsState()
            val project = remember(activeProjectId, projects) {
                activeProjectId?.let { id -> projects.find { it.id == id } }
            }
            if (project != null) {
                SpecWizardSheet(
                    persistenceStore = persistenceStore,
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
            imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
            contentDescription = if (isExpanded) "Collapse specs" else "Expand specs",
            tint = LocalDSColors.current.textMuted,
            modifier = Modifier.size(DSLayout.chevronFrameWidth),
        )
        Spacer(Modifier.width(DSSpacing.xs))
        Text(
            text = "SPECS",
            style = DSFont.sidebarSection,
            color = LocalDSColors.current.textSecondary,
        )
        if (total > 0) {
            Spacer(Modifier.width(DSSpacing.xs))
            Text(
                text = "$passing/$total",
                style = DSFont.sidebarItemSmall,
                color = if (allPassing) LocalDSColors.current.gitAdded else LocalDSColors.current.gitModified,
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
                tint = LocalDSColors.current.textMuted,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}

@Composable
private fun SpecListRow(
    spec: SpecFile,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) LocalDSColors.current.accentPrimary.copy(alpha = 0.12f) else Color.Transparent,
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
        Spacer(Modifier.width(DSLayout.chevronFrameWidth))
        Spacer(Modifier.width(DSSpacing.xs))

        Box(
            modifier = Modifier
                .size(DSLayout.indicatorSize)
                .background(color = specStatusColor(spec.status, LocalDSColors.current), shape = CircleShape),
        )
        Spacer(Modifier.width(DSSpacing.xs))

        Text(
            text = spec.name,
            style = DSFont.sidebarItem,
            color = if (isSelected) LocalDSColors.current.textPrimary else LocalDSColors.current.textSecondary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )

        when (spec.status) {
            SpecStatus.PASSING -> Text(
                text = "✓",
                style = DSFont.statusBadge,
                color = LocalDSColors.current.gitAdded,
            )
            SpecStatus.FAILING -> Text(
                text = "×",
                style = DSFont.statusBadge,
                color = LocalDSColors.current.gitDeleted,
            )
            SpecStatus.UNKNOWN -> Unit
        }
        Spacer(Modifier.width(DSSpacing.xs))
    }
}

@Composable
private fun GeneratedSectionHeader(
    count: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(DSLayout.gitSectionHeaderHeight)
            .clickable { onToggleExpand() }
            .padding(start = DSSpacing.sm, end = DSSpacing.md),
    ) {
        Icon(
            imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
            contentDescription = if (isExpanded) "Collapse generated" else "Expand generated",
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
            text = "$count",
            style = DSFont.sidebarItemSmall,
            color = LocalDSColors.current.textMuted,
        )
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = onRefresh,
            modifier = Modifier.size(DSLayout.gitSectionHeaderHeight),
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Refresh generated files",
                tint = LocalDSColors.current.textMuted,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}

@Composable
private fun GeneratedFileRow(
    file: studio.vibe.shared.model.GeneratedFile,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) LocalDSColors.current.accentPrimary.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(durationMillis = 120),
        label = "generatedRowBg",
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
        Spacer(Modifier.width(DSLayout.chevronFrameWidth))
        Spacer(Modifier.width(DSSpacing.xs))
        Icon(
            imageVector = Icons.Filled.Description,
            contentDescription = null,
            tint = LocalDSColors.current.textMuted,
            modifier = Modifier.size(10.dp),
        )
        Spacer(Modifier.width(DSSpacing.xs))
        Text(
            text = file.name,
            style = DSFont.sidebarItem,
            color = if (isSelected) LocalDSColors.current.textPrimary else LocalDSColors.current.textSecondary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}

internal fun specStatusColor(status: SpecStatus, colors: DSColors): androidx.compose.ui.graphics.Color = when (status) {
    SpecStatus.PASSING -> colors.gitAdded
    SpecStatus.FAILING -> colors.gitDeleted
    SpecStatus.UNKNOWN -> colors.indicatorIdle
}

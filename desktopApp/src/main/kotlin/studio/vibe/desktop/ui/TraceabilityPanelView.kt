@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import studio.vibe.shared.core.common.PersistenceStore
import studio.vibe.shared.core.common.project.ProjectManaging
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSLayout
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.shared.feature.codespeak.presentation.FileTraceabilityEntry
import studio.vibe.shared.feature.codespeak.presentation.TraceabilityEntry
import studio.vibe.shared.feature.codespeak.presentation.TraceabilityPanelViewModel

/**
 * Right-side panel showing spec <-> source file cross-references.
 *
 * Scans the active project for `@file:` markers in source files and shows
 * a spec-name-grouped list of which source files reference each spec.
 *
 * Mirrors TraceabilityPanelView.swift — same structure and terminology.
 *
 * @param container  DI container (provides persistenceStore, projectStore, scope).
 * @param onClose    Called when the user presses the close (X) button.
 * @param modifier   Modifier applied to the root [Column].
 */
@Composable
public fun TraceabilityPanelView(
    persistenceStore: PersistenceStore,
    coroutineScope: CoroutineScope,
    projectStore: ProjectManaging,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm = remember {
        TraceabilityPanelViewModel(
            persistenceStore = persistenceStore,
            parentScope = coroutineScope,
        )
    }
    DisposableEffect(vm) { onDispose { vm.dispose() } }

    val state by vm.state.collectAsState()

    val activeProjectId by projectStore.activeProjectId.collectAsState()
    val projects by projectStore.projects.collectAsState()
    val activeProject by remember(activeProjectId, projects) {
        derivedStateOf { activeProjectId?.let { id -> projects.find { it.id == id } } }
    }

    // Scan when active project changes
    LaunchedEffect(activeProject?.path) {
        activeProject?.let { vm.scanProject(it.path) }
    }

    Column(
        modifier = modifier
            .background(LocalDSColors.current.surfaceRaised),
    ) {
        // ── Header ─────────────────────────────────────────────────────────
        TraceabilityHeader(
            isLoading = state.isLoading,
            onRefresh = { activeProject?.let { vm.refresh(it.path) } },
            onClose = onClose,
        )
        HorizontalDivider(color = LocalDSColors.current.borderSubtle, thickness = 1.dp)

        // ── Body ───────────────────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading -> TraceabilityLoadingState()
                state.entries.isEmpty() && state.fileEntries.isEmpty() -> TraceabilityEmptyState()
                else -> TraceabilityContent(
                    entries = state.entries,
                    fileEntries = state.fileEntries,
                )
            }
        }

        // ── Error banner ───────────────────────────────────────────────────
        if (state.errorMessage != null) {
            HorizontalDivider(color = LocalDSColors.current.borderSubtle, thickness = 1.dp)
            Text(
                text = state.errorMessage!!,
                style = DSFont.sidebarItemSmall,
                color = LocalDSColors.current.gitDeleted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DSSpacing.md, vertical = DSSpacing.xs),
            )
        }
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun TraceabilityHeader(
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(DSLayout.gitSectionHeaderHeight)
            .padding(horizontal = DSSpacing.md),
    ) {
        Icon(
            imageVector = Icons.Filled.Link,
            contentDescription = null,
            tint = LocalDSColors.current.agentCodeSpeak,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(DSSpacing.xs))
        Text(
            text = "Traceability",
            style = DSFont.sidebarSection,
            color = LocalDSColors.current.textPrimary,
        )
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = onRefresh,
            enabled = !isLoading,
            modifier = Modifier.size(DSLayout.gitSectionHeaderHeight),
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Refresh traceability map",
                tint = LocalDSColors.current.textMuted,
                modifier = Modifier.size(11.dp),
            )
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(DSLayout.gitSectionHeaderHeight),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close traceability panel",
                tint = LocalDSColors.current.textMuted,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}

// ── Content ───────────────────────────────────────────────────────────────────

@Composable
private fun TraceabilityContent(
    entries: List<TraceabilityEntry>,
    fileEntries: List<FileTraceabilityEntry>,
) {
    var specsExpanded by rememberSaveable { mutableStateOf(true) }
    var filesExpanded by rememberSaveable { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = DSSpacing.sm, vertical = DSSpacing.xs),
    ) {
        // \u2500\u2500 SPECS \u2192 FILES \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
        item(key = "header-specs-files") {
            CollapsibleSectionHeader(
                title = "SPECS \u2192 FILES",
                count = entries.size,
                expanded = specsExpanded,
                onToggle = { specsExpanded = !specsExpanded },
            )
        }
        if (specsExpanded) {
            items(items = entries, key = { "sf-${it.specName}" }) { entry ->
                TraceabilityEntryCard(entry = entry)
            }
        }

        // \u2500\u2500 FILES \u2192 SPECS \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
        if (fileEntries.isNotEmpty()) {
            item(key = "header-files-specs") {
                CollapsibleSectionHeader(
                    title = "FILES \u2192 SPECS",
                    count = fileEntries.size,
                    expanded = filesExpanded,
                    onToggle = { filesExpanded = !filesExpanded },
                )
            }
            if (filesExpanded) {
                items(items = fileEntries, key = { "fs-${it.filePath.path}" }) { entry ->
                    FileTraceabilityCard(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun CollapsibleSectionHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val chevronRotation by animateFloatAsState(if (expanded) 90f else 0f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = DSSpacing.xs, vertical = DSSpacing.xs)
            .padding(top = DSSpacing.sm),
    ) {
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = LocalDSColors.current.textMuted,
            modifier = Modifier.size(12.dp).rotate(chevronRotation),
        )
        Spacer(Modifier.width(DSSpacing.xxs))
        Text(
            text = title,
            style = DSFont.sidebarSection,
            color = LocalDSColors.current.textMuted,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = DSFont.sidebarItemSmall,
            color = LocalDSColors.current.textDisabled,
        )
    }
}

@Composable
private fun TraceabilityEntryCard(entry: TraceabilityEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = DSSpacing.xs)
            .clip(RoundedCornerShape(DSRadius.sm))
            .background(LocalDSColors.current.surfaceOverlay.copy(alpha = 0.4f)),
    ) {
        // Spec name row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(DSLayout.gitFileRowHeight)
                .padding(horizontal = DSSpacing.xs),
        ) {
            Icon(
                imageVector = Icons.Filled.TextSnippet,
                contentDescription = null,
                tint = LocalDSColors.current.agentCodeSpeak,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(DSSpacing.xs))
            Text(
                text = entry.specName,
                style = DSFont.sidebarItem,
                color = LocalDSColors.current.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Linked source files
        entry.linkedFiles.forEach { filePath ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = DSSpacing.md, end = DSSpacing.xs, bottom = DSSpacing.xxs),
            ) {
                Text(
                    text = "\u2192",
                    style = DSFont.sidebarItemSmall,
                    color = LocalDSColors.current.textMuted,
                    modifier = Modifier.width(DSLayout.statusLetterWidth),
                )
                Text(
                    text = filePath.name,
                    style = DSFont.sidebarItemSmall,
                    color = LocalDSColors.current.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(DSSpacing.xxs))
    }
}

@Composable
private fun FileTraceabilityCard(entry: FileTraceabilityEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = DSSpacing.xs)
            .clip(RoundedCornerShape(DSRadius.sm))
            .background(LocalDSColors.current.surfaceOverlay.copy(alpha = 0.4f)),
    ) {
        // File name row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(DSLayout.gitFileRowHeight)
                .padding(horizontal = DSSpacing.xs),
        ) {
            Icon(
                imageVector = Icons.Filled.Description,
                contentDescription = null,
                tint = LocalDSColors.current.textSecondary,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(DSSpacing.xs))
            Text(
                text = entry.filePath.name,
                style = DSFont.sidebarItem,
                color = LocalDSColors.current.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Referencing specs
        entry.referencedBySpecs.forEach { specName ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = DSSpacing.md, end = DSSpacing.xs, bottom = DSSpacing.xxs),
            ) {
                Text(
                    text = "→",
                    style = DSFont.sidebarItemSmall,
                    color = LocalDSColors.current.agentCodeSpeak,
                    modifier = Modifier.width(DSLayout.statusLetterWidth),
                )
                Text(
                    text = specName,
                    style = DSFont.sidebarItemSmall,
                    color = LocalDSColors.current.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(DSSpacing.xxs))
    }
}

// ── Empty states ──────────────────────────────────────────────────────────────

@Composable
private fun TraceabilityLoadingState() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = LocalDSColors.current.accentPrimary,
            strokeWidth = 2.dp,
        )
    }
}

@Composable
private fun TraceabilityEmptyState() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.Link,
                contentDescription = null,
                tint = LocalDSColors.current.textMuted,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(DSSpacing.sm))
            Text(
                text = "No traceability links",
                style = DSFont.sidebarItem,
                color = LocalDSColors.current.textMuted,
            )
            Spacer(Modifier.height(DSSpacing.xxs))
            Text(
                text = "Add @file: markers to specs",
                style = DSFont.sidebarItemSmall,
                color = LocalDSColors.current.textDisabled,
            )
        }
    }
}

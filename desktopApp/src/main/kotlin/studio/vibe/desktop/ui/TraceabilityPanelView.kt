@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import studio.vibe.shared.contract.PersistenceStore
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSLayout
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.shared.viewmodel.TraceabilityEntry
import studio.vibe.shared.viewmodel.TraceabilityPanelViewModel

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
            scope = coroutineScope,
        )
    }

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
                state.entries.isEmpty() -> TraceabilityEmptyState()
                else -> TraceabilityContent(entries = state.entries)
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
private fun TraceabilityContent(entries: List<TraceabilityEntry>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = DSSpacing.sm, vertical = DSSpacing.xs),
    ) {
        // Section header
        item {
            TraceabilitySectionHeader(title = "SPECS \u2192 FILES")
        }

        items(
            items = entries,
            key = { it.specName },
        ) { entry ->
            TraceabilityEntryCard(entry = entry)
        }
    }
}

@Composable
private fun TraceabilitySectionHeader(title: String) {
    Text(
        text = title,
        style = DSFont.sidebarSection,
        color = LocalDSColors.current.textMuted,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DSSpacing.xs, vertical = DSSpacing.xs)
            .padding(top = DSSpacing.sm),
    )
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

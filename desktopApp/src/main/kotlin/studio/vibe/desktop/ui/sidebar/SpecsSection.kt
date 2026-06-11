@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.uuid.Uuid
import studio.vibe.shared.feature.filetree.data.FileTreeBuilder
import studio.vibe.desktop.ui.theme.DSColors
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSLayout
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.shared.feature.filetree.domain.FileEntry
import studio.vibe.shared.feature.filetree.domain.FileTreeNode
import studio.vibe.shared.core.common.project.Project
import studio.vibe.shared.feature.codespeak.domain.model.SpecStatus

@Composable
internal fun SpecsSection(
    fileTreeBuilder: FileTreeBuilder,
    projects: List<Project>,
    activeProjectId: Uuid?,
) {
    var fileTrees by remember { mutableStateOf(mapOf<Uuid, List<FileTreeNode>>()) }

    val activeProject = projects.find { it.id == activeProjectId }
    if (activeProject != null && activeProjectId != null && activeProjectId !in fileTrees) {
        LaunchedEffect(activeProjectId) {
            val tree = fileTreeBuilder.buildTree(root = activeProject.path, maxDepth = 5)
            fileTrees = fileTrees + (activeProjectId to tree)
        }
    }

    val specEntries: List<FileEntry> = remember(fileTrees, activeProjectId) {
        val nodes = activeProjectId?.let { fileTrees[it] } ?: emptyList()
        collectSpecFiles(nodes)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DSLayout.gitSectionHeaderHeight)
                .padding(horizontal = DSLayout.sidebarHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("SPECS", style = DSFont.sidebarSection, color = LocalDSColors.current.textSecondary)
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(LocalDSColors.current.borderDefault))

        when {
            activeProject == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No project selected", style = DSFont.sidebarItem, color = LocalDSColors.current.textMuted)
                }
            }
            activeProjectId !in fileTrees -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading...", style = DSFont.sidebarItem, color = LocalDSColors.current.textMuted)
                }
            }
            specEntries.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = LocalDSColors.current.textMuted,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.height(DSSpacing.sm))
                        Text("No specs found", style = DSFont.sidebarItem, color = LocalDSColors.current.textMuted)
                        Spacer(Modifier.height(DSSpacing.xxs))
                        Text("*.cs.md", style = DSFont.sidebarItemSmall, color = LocalDSColors.current.textDisabled)
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(
                        horizontal = DSLayout.sidebarHorizontalPadding,
                        vertical = DSSpacing.xs,
                    ),
                ) {
                    items(specEntries, key = { it.path.path }) { entry ->
                        SpecSidebarRow(entry = entry)
                    }
                }
            }
        }
    }
}

private fun collectSpecFiles(nodes: List<FileTreeNode>): List<FileEntry> {
    val result = mutableListOf<FileEntry>()
    for (node in nodes) {
        when (node) {
            is FileTreeNode.File -> if (node.entry.path.name.endsWith(".cs.md")) result.add(node.entry)
            is FileTreeNode.Directory -> result.addAll(collectSpecFiles(node.entry.children))
        }
    }
    return result
}

@Composable
private fun SpecSidebarRow(entry: FileEntry) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val specName = entry.path.name.removeSuffix(".md").removeSuffix(".cs")
    val status = SpecStatus.UNKNOWN

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DSLayout.treeRowHeight)
            .hoverable(interactionSource)
            .background(if (isHovered) LocalDSColors.current.hoverOverlay else androidx.compose.ui.graphics.Color.Transparent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(DSLayout.indicatorSize)
                .clip(RoundedCornerShape(50))
                .background(specSidebarStatusColor(status, LocalDSColors.current)),
        )

        Spacer(Modifier.width(DSSpacing.xs))

        Text(
            text = specName,
            style = DSFont.sidebarItem,
            color = LocalDSColors.current.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun specSidebarStatusColor(status: SpecStatus, colors: DSColors): androidx.compose.ui.graphics.Color = when (status) {
    SpecStatus.PASSING -> colors.gitAdded
    SpecStatus.FAILING -> colors.gitDeleted
    SpecStatus.UNKNOWN -> colors.indicatorIdle
}

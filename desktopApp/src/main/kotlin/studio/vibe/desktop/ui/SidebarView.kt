@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import studio.vibe.shared.contract.GitServicing
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.service.filetree.FileTreeBuilder
import studio.vibe.desktop.ui.sidebar.GitBranchSection
import studio.vibe.desktop.ui.sidebar.IconStrip
import studio.vibe.desktop.ui.sidebar.MultiProjectFileTree
import studio.vibe.desktop.ui.sidebar.SidebarTab
import studio.vibe.desktop.ui.sidebar.SpecsSection
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.shared.viewmodel.GitSidebarViewModel

/**
 * Root sidebar composable.
 *
 * Houses the icon strip and main content column. The GIT tab hosts
 * a full branch list with context menus and a "New Branch" action button.
 *
 * Sub-composables live in [studio.vibe.desktop.ui.sidebar] package:
 * [IconStrip], [MultiProjectFileTree], [GitBranchSection], [SpecsSection].
 */
@Composable
fun SidebarView(
    projectStore: ProjectManaging,
    fileTreeBuilder: FileTreeBuilder,
    gitSidebarViewModel: GitSidebarViewModel,
    gitService: GitServicing,
    coroutineScope: CoroutineScope,
    onToggleGitPanel: () -> Unit,
    onOpenProject: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val projects by projectStore.projects.collectAsState()
    val activeProjectId by projectStore.activeProjectId.collectAsState()
    var activeTab by remember { mutableStateOf(SidebarTab.FILES) }

    Row(modifier = modifier.background(LocalDSColors.current.surfaceRaised)) {
        IconStrip(
            projectStore = projectStore,
            activeTab = activeTab,
            onTabSelected = { activeTab = it },
            onAddProject = onOpenProject,
        )

        Box(
            Modifier.width(1.dp).fillMaxHeight().background(LocalDSColors.current.borderDefault),
        )

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            when (activeTab) {
                SidebarTab.FILES -> MultiProjectFileTree(
                    projectStore = projectStore,
                    fileTreeBuilder = fileTreeBuilder,
                    gitService = gitService,
                    coroutineScope = coroutineScope,
                    projects = projects,
                    activeProjectId = activeProjectId,
                )
                SidebarTab.GIT -> GitBranchSection(
                    projectStore = projectStore,
                    gitSidebarViewModel = gitSidebarViewModel,
                    gitService = gitService,
                    coroutineScope = coroutineScope,
                )
                SidebarTab.SPECS -> SpecsSection(
                    fileTreeBuilder = fileTreeBuilder,
                    projects = projects,
                    activeProjectId = activeProjectId,
                )
            }
        }
    }
}

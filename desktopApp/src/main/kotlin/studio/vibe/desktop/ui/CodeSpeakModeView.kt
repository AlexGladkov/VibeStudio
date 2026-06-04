@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import studio.vibe.shared.contract.PersistenceStore
import studio.vibe.shared.contract.ProcessRunner
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.desktop.ui.codespeak.BuildOutputColumn
import studio.vibe.desktop.ui.codespeak.CodeSpeakResizeHandle
import studio.vibe.desktop.ui.codespeak.EditorColumn
import studio.vibe.desktop.ui.codespeak.SpecsListColumn
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.shared.viewmodel.CodeSpeakModeViewModel
import studio.vibe.shared.viewmodel.SpecBuildPanelViewModel

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
 * Sub-composables live in [studio.vibe.desktop.ui.codespeak] package:
 * [SpecsListColumn], [EditorColumn], [BuildOutputColumn], [CodeSpeakResizeHandle].
 *
 * @param persistenceStore Application-scoped persistence.
 * @param projectStore     Application-scoped project manager.
 * @param processRunner    Platform subprocess runner.
 * @param coroutineScope   Container-scoped coroutine scope.
 * @param modifier         Applied to the outermost [Row].
 */
@Composable
public fun CodeSpeakModeView(
    persistenceStore: PersistenceStore,
    projectStore: ProjectManaging,
    processRunner: ProcessRunner,
    coroutineScope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val modeVm = remember {
        CodeSpeakModeViewModel(
            persistenceStore = persistenceStore,
            projectManaging = projectStore,
            parentScope = coroutineScope,
        )
    }
    DisposableEffect(modeVm) { onDispose { modeVm.dispose() } }

    val buildVm = remember {
        SpecBuildPanelViewModel(
            processRunner = processRunner,
            projectManaging = projectStore,
            parentScope = coroutineScope,
        )
    }
    DisposableEffect(buildVm) { onDispose { buildVm.dispose() } }

    val modeState by modeVm.state.collectAsState()
    val buildState by buildVm.state.collectAsState()

    val activeProjectId by projectStore.activeProjectId.collectAsState()
    val projects by projectStore.projects.collectAsState()
    val activeProject by remember(activeProjectId, projects) {
        derivedStateOf { activeProjectId?.let { id -> projects.find { it.id == id } } }
    }

    LaunchedEffect(activeProjectId) {
        buildVm.resetForProject(activeProjectId)
        activeProjectId?.let { modeVm.loadSpecs(it) }
    }

    LaunchedEffect(activeProjectId) {
        activeProjectId?.let { modeVm.scanGeneratedFiles(it) }
    }

    var specsWidth by remember { mutableStateOf(220.dp) }
    var buildWidth by remember { mutableStateOf(280.dp) }
    val density = LocalDensity.current

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(LocalDSColors.current.surfaceBase)
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
        SpecsListColumn(
            persistenceStore = persistenceStore,
            projectStore = projectStore,
            coroutineScope = coroutineScope,
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

        CodeSpeakResizeHandle(
            onDrag = { delta ->
                val newWidth = specsWidth + with(density) { delta.toDp() }
                specsWidth = newWidth.coerceIn(160.dp, 380.dp)
            },
        )

        EditorColumn(
            selectedSpec = modeState.selectedSpec,
            editorContent = modeState.editorContent,
            isEditorDirty = modeState.isEditorDirty,
            onContentChange = { modeVm.updateEditorContent(it) },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )

        CodeSpeakResizeHandle(
            onDrag = { delta ->
                val newWidth = buildWidth - with(density) { delta.toDp() }
                buildWidth = newWidth.coerceIn(200.dp, 520.dp)
            },
        )

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

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import kotlin.uuid.Uuid
import studio.vibe.shared.core.common.project.ProjectManaging
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.shared.feature.settings.presentation.ProjectSettingsViewModel

/**
 * DialogWindow for per-project settings.
 *
 * Port of SwiftUI `ProjectSettingsSheet`. Currently exposes the production URL
 * field (used by the "Open in Browser" toolbar action). Additional settings
 * (default branch, shell path, etc.) can be added in further iterations.
 *
 * @param container     Desktop DI container.
 * @param projectId     UUID of the project to configure.
 * @param projectName   Display name shown in the dialog subtitle.
 * @param projectPath   Filesystem path shown read-only under the name.
 * @param onDismiss     Called on Cancel and after a successful save.
 */
@Composable
fun ProjectSettingsSheet(
    projectStore: ProjectManaging,
    projectId: Uuid,
    projectName: String,
    projectPath: String,
    onDismiss: () -> Unit,
) {
    val dialogState = rememberDialogState(
        position = WindowPosition(Alignment.Center),
        width = 400.dp,
        height = 280.dp,
    )

    val coroutineScope = rememberCoroutineScope()
    val vm = remember(projectId) {
        ProjectSettingsViewModel(
            projectManaging = projectStore,
            parentScope = coroutineScope,
        ).also { it.load(projectId) }
    }
    DisposableEffect(vm) { onDispose { vm.dispose() } }

    val state by vm.state.collectAsState()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onDismiss()
    }

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = "Project Settings",
        resizable = false,
    ) {
        ProjectSettingsContent(
            projectName = projectName,
            projectPath = projectPath,
            productionURL = state.productionURL,
            errorMessage = state.errorMessage,
            onProductionURLChange = { vm.updateProductionURL(it) },
            onCancel = onDismiss,
            onSave = { vm.saveProductionURL(projectId) },
        )
    }
}

// ── Content ───────────────────────────────────────────────────────────────────

@Composable
private fun ProjectSettingsContent(
    projectName: String,
    projectPath: String,
    productionURL: String,
    errorMessage: String?,
    onProductionURLChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalDSColors.current.surfaceOverlay)
            .padding(DSSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(DSSpacing.lg),
    ) {
        // Header
        Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
            Text(
                text = "Project Settings",
                style = DSFont.settingsTitle,
                color = LocalDSColors.current.textPrimary,
            )
            Text(
                text = projectName,
                style = DSFont.sidebarItemSmall,
                color = LocalDSColors.current.textMuted,
            )
            Text(
                text = projectPath,
                style = DSFont.monoSmall,
                color = LocalDSColors.current.textMuted,
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(LocalDSColors.current.borderDefault),
        )

        // Production URL field
        Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
            Text(
                text = "Production URL",
                style = DSFont.sidebarItemSmall,
                color = LocalDSColors.current.textSecondary,
            )
            OutlinedTextField(
                value = productionURL,
                onValueChange = onProductionURLChange,
                placeholder = {
                    Text(
                        "https://example.com",
                        style = DSFont.commitInput,
                        color = LocalDSColors.current.textMuted,
                    )
                },
                textStyle = DSFont.commitInput.copy(color = LocalDSColors.current.textPrimary),
                singleLine = true,
                isError = errorMessage != null,
                shape = RoundedCornerShape(DSRadius.md),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LocalDSColors.current.borderFocus,
                    unfocusedBorderColor = LocalDSColors.current.borderDefault,
                    errorBorderColor = LocalDSColors.current.gitDeleted,
                    cursorColor = LocalDSColors.current.accentPrimary,
                    focusedContainerColor = LocalDSColors.current.surfaceInput,
                    unfocusedContainerColor = LocalDSColors.current.surfaceInput,
                    errorContainerColor = LocalDSColors.current.surfaceInput,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                            onSave(); true
                        } else false
                    },
            )
            Text(
                text = "Used for the \"Open in Browser\" toolbar action",
                style = DSFont.monoSmall,
                color = LocalDSColors.current.textMuted,
            )
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = DSFont.sidebarItemSmall,
                    color = LocalDSColors.current.gitDeleted,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DSSpacing.sm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(DSRadius.md),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalDSColors.current.textSecondary),
            ) {
                Text("Cancel", style = DSFont.buttonLabel)
            }

            Button(
                onClick = onSave,
                shape = RoundedCornerShape(DSRadius.md),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalDSColors.current.buttonPrimaryBg,
                    contentColor = LocalDSColors.current.buttonPrimaryText,
                    disabledContainerColor = LocalDSColors.current.surfaceOverlay,
                    disabledContentColor = LocalDSColors.current.textDisabled,
                ),
                modifier = Modifier.width(80.dp),
            ) {
                Text("Save", style = DSFont.buttonLabel)
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

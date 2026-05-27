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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import kotlin.uuid.Uuid
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.viewmodel.GitRemoteSetupViewModel

/**
 * DialogWindow for configuring a git remote on the active project.
 *
 * Backs the Swift `GitRemoteSetupSheet`. Loads any existing "origin" URL on
 * open, lets the user edit both the remote name and URL, and calls
 * `gitService.addRemote` on confirmation.
 *
 * @param container     Desktop DI container.
 * @param projectId     UUID of the project to configure.
 * @param projectPath   Root path of the git repository.
 * @param projectName   Display name shown in the dialog subtitle.
 * @param onDismiss     Called on Cancel and after a successful save.
 */
@Composable
fun GitRemoteSetupSheet(
    container: DesktopServiceContainer,
    projectId: Uuid,
    projectPath: FilePath,
    projectName: String,
    onDismiss: () -> Unit,
) {
    val dialogState = rememberDialogState(
        position = WindowPosition(Alignment.Center),
        width = 420.dp,
        height = 320.dp,
    )

    val vm = remember(projectId) {
        GitRemoteSetupViewModel(
            gitService = container.gitService,
            scope = container.scope,
        ).also { it.loadExistingRemote(projectPath) }
    }

    val state by vm.state.collectAsState()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onDismiss()
    }

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = "Remote Repository",
        resizable = false,
    ) {
        GitRemoteSetupContent(
            projectName = projectName,
            remoteName = state.remoteName,
            remoteURL = state.remoteURL,
            isLoading = state.isLoading,
            errorMessage = state.errorMessage,
            onRemoteNameChange = { vm.updateRemoteName(it) },
            onRemoteURLChange = { vm.updateRemoteURL(it) },
            onCancel = onDismiss,
            onSave = { vm.addRemote(projectPath) },
        )
    }
}

// ── Content ───────────────────────────────────────────────────────────────────

@Composable
private fun GitRemoteSetupContent(
    projectName: String,
    remoteName: String,
    remoteURL: String,
    isLoading: Boolean,
    errorMessage: String?,
    onRemoteNameChange: (String) -> Unit,
    onRemoteURLChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val canSave = remoteName.isNotBlank() && remoteURL.isNotBlank() && !isLoading

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DSColor.surfaceOverlay)
            .padding(DSSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(DSSpacing.lg),
    ) {
        // Header
        Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
            Text(
                text = "Remote Repository",
                style = DSFont.settingsTitle,
                color = DSColor.textPrimary,
            )
            Text(
                text = projectName,
                style = DSFont.sidebarItemSmall,
                color = DSColor.textMuted,
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DSColor.borderDefault),
        )

        // Remote name field
        Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
            Text(
                text = "Remote name",
                style = DSFont.sidebarItemSmall,
                color = DSColor.textSecondary,
            )
            OutlinedTextField(
                value = remoteName,
                onValueChange = onRemoteNameChange,
                placeholder = {
                    Text("origin", style = DSFont.commitInput, color = DSColor.textMuted)
                },
                textStyle = DSFont.commitInput.copy(color = DSColor.textPrimary),
                singleLine = true,
                shape = RoundedCornerShape(DSRadius.md),
                colors = styledTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Remote URL field
        Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
            Text(
                text = "Repository URL",
                style = DSFont.sidebarItemSmall,
                color = DSColor.textSecondary,
            )
            OutlinedTextField(
                value = remoteURL,
                onValueChange = onRemoteURLChange,
                placeholder = {
                    Text(
                        "https://github.com/user/repo.git",
                        style = DSFont.commitInput,
                        color = DSColor.textMuted,
                    )
                },
                textStyle = DSFont.commitInput.copy(color = DSColor.textPrimary),
                singleLine = true,
                isError = errorMessage != null,
                shape = RoundedCornerShape(DSRadius.md),
                colors = styledTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }

        // Error message
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = DSFont.sidebarItemSmall,
                color = DSColor.gitDeleted,
            )
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
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DSColor.textSecondary),
            ) {
                Text("Cancel", style = DSFont.buttonLabel)
            }

            Button(
                onClick = onSave,
                enabled = canSave,
                shape = RoundedCornerShape(DSRadius.md),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DSColor.buttonPrimaryBg,
                    contentColor = DSColor.buttonPrimaryText,
                    disabledContainerColor = DSColor.surfaceOverlay,
                    disabledContentColor = DSColor.textDisabled,
                ),
                modifier = Modifier.width(110.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = DSColor.buttonPrimaryText,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Add Remote", style = DSFont.buttonLabel)
                }
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun styledTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DSColor.borderFocus,
    unfocusedBorderColor = DSColor.borderDefault,
    errorBorderColor = DSColor.gitDeleted,
    cursorColor = DSColor.accentPrimary,
    focusedContainerColor = DSColor.surfaceInput,
    unfocusedContainerColor = DSColor.surfaceInput,
    errorContainerColor = DSColor.surfaceInput,
)

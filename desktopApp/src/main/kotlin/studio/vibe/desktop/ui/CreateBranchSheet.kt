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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import kotlinx.coroutines.CoroutineScope
import studio.vibe.shared.contract.GitServicing
import studio.vibe.shared.viewmodel.GitSidebarViewModel
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.viewmodel.CreateBranchViewModel

/** Regex for valid git branch names: letters, digits, hyphens, underscores, dots, and slashes. */
private val VALID_BRANCH_REGEX = Regex("^[a-zA-Z0-9._/][a-zA-Z0-9._/\\-]*$")

/**
 * DialogWindow for creating a new git branch.
 *
 * Instantiates a [CreateBranchViewModel] scoped to the dialog lifetime,
 * loads available branches on first composition, and delegates create/cancel
 * actions back to the caller via [onDismiss].
 *
 * @param container Desktop DI container providing gitService and coroutine scope.
 * @param projectId UUID of the project in which the branch will be created.
 * @param projectPath Filesystem path to the git repository root.
 * @param fromBranch Optional base branch name to pre-fill and display in the UI.
 *                   When null, the current HEAD branch is used by default.
 * @param onDismiss Called on both Cancel press and successful branch creation.
 */
@Composable
fun CreateBranchSheet(
    gitService: GitServicing,
    coroutineScope: CoroutineScope,
    gitSidebarViewModel: GitSidebarViewModel,
    projectId: Uuid,
    projectPath: FilePath,
    fromBranch: String? = null,
    onDismiss: () -> Unit,
) {
    val dialogState = rememberDialogState(
        position = WindowPosition(Alignment.Center),
        width = 400.dp,
        height = 250.dp,
    )

    val vm = remember(projectId) {
        CreateBranchViewModel(
            gitService = gitService,
            scope = coroutineScope,
        ).also { vm ->
            vm.loadBranches(projectPath)
            if (fromBranch != null) {
                vm.updateBaseBranch(fromBranch)
            }
        }
    }

    val state by vm.state.collectAsState()

    // Close the dialog after successful creation
    LaunchedEffect(state.isCreated) {
        if (state.isCreated) {
            gitSidebarViewModel.loadGitInfo(projectId, projectPath)
            onDismiss()
        }
    }

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = "New Branch",
        resizable = false,
    ) {
        CreateBranchContent(
            branchName = state.branchName,
            fromBranch = fromBranch,
            isCreating = state.isCreating,
            errorMessage = state.errorMessage,
            onBranchNameChange = { vm.updateBranchName(it) },
            onCancel = onDismiss,
            onCreate = { vm.createBranch(projectPath) },
        )
    }
}

@Composable
private fun CreateBranchContent(
    branchName: String,
    fromBranch: String?,
    isCreating: Boolean,
    errorMessage: String?,
    onBranchNameChange: (String) -> Unit,
    onCancel: () -> Unit,
    onCreate: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    val isValid = branchName.isNotBlank() && VALID_BRANCH_REGEX.matches(branchName)
    val canCreate = isValid && !isCreating

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalDSColors.current.surfaceOverlay)
            .padding(DSSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(DSSpacing.lg),
    ) {
        // Title and from-branch subtitle
        Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
            Text(
                text = "New Branch",
                style = DSFont.settingsTitle,
                color = LocalDSColors.current.textPrimary,
            )
            if (fromBranch != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs),
                ) {
                    Text(
                        text = "from",
                        style = DSFont.sidebarItemSmall,
                        color = LocalDSColors.current.textMuted,
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.CallSplit,
                        contentDescription = null,
                        tint = LocalDSColors.current.accentPrimary,
                        modifier = Modifier.size(DSFont.iconBase.value.dp),
                    )
                    Text(
                        text = fromBranch,
                        style = DSFont.sidebarItemSmall,
                        color = LocalDSColors.current.accentPrimary,
                    )
                }
            }
        }

        // 1dp divider
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(LocalDSColors.current.borderDefault),
        )

        // Branch name input
        Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
            Text(
                text = "Branch name",
                style = DSFont.sidebarItemSmall,
                color = LocalDSColors.current.textSecondary,
            )
            OutlinedTextField(
                value = branchName,
                onValueChange = onBranchNameChange,
                placeholder = {
                    Text(
                        "feature/my-feature",
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
                        if (event.type == KeyEventType.KeyUp && event.key == Key.Enter && canCreate) {
                            onCreate()
                            true
                        } else {
                            false
                        }
                    },
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
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = LocalDSColors.current.textSecondary,
                ),
            ) {
                Text("Cancel", style = DSFont.buttonLabel)
            }

            Button(
                onClick = onCreate,
                enabled = canCreate,
                shape = RoundedCornerShape(DSRadius.md),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalDSColors.current.buttonPrimaryBg,
                    contentColor = LocalDSColors.current.buttonPrimaryText,
                    disabledContainerColor = LocalDSColors.current.surfaceOverlay,
                    disabledContentColor = LocalDSColors.current.textDisabled,
                ),
                modifier = Modifier.width(100.dp),
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = LocalDSColors.current.buttonPrimaryText,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Create", style = DSFont.buttonLabel)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

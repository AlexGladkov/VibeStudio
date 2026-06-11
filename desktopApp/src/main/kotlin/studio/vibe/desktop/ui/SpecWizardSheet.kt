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
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.launch
import studio.vibe.shared.core.common.PersistenceStore
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.shared.core.common.FilePath
import kotlin.uuid.Uuid

/**
 * DialogWindow for creating a new .cs.md spec file.
 *
 * Asks for a file name (sanitised to lowercase-with-hyphens), writes an empty
 * template to `spec/<name>.cs.md` inside the project directory, then calls
 * [onCreated] and dismisses.
 *
 * Mirrors SpecWizardSheet.swift: 400 x 240 sheet.
 *
 * @param container   DI container providing persistence store and coroutine scope.
 * @param projectId   Active project ID used to load specs after creation.
 * @param projectPath Path to the project root directory.
 * @param onCreated   Called after the spec file is created (refresh spec list here).
 * @param onDismiss   Called on Cancel or after creation.
 */
@Composable
public fun SpecWizardSheet(
    persistenceStore: PersistenceStore,
    projectId: Uuid,
    projectPath: FilePath,
    onCreated: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dialogState = rememberDialogState(
        position = WindowPosition(Alignment.Center),
        width = 400.dp,
        height = 240.dp,
    )

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = "New Spec",
        resizable = false,
    ) {
        SpecWizardContent(
            persistenceStore = persistenceStore,
            projectPath = projectPath,
            onCreated = {
                onCreated()
                onDismiss()
            },
            onCancel = onDismiss,
        )
    }
}

@Composable
private fun SpecWizardContent(
    persistenceStore: PersistenceStore,
    projectPath: FilePath,
    onCreated: () -> Unit,
    onCancel: () -> Unit,
) {
    var specName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    val sanitized = specName
        .trim()
        .lowercase()
        .replace(" ", "-")
        .filter { it.isLetterOrDigit() || it == '-' || it == '_' }

    val canCreate = sanitized.isNotEmpty() && !isSaving

    fun createSpec() {
        if (!canCreate) return
        isSaving = true
        errorMessage = null
        scope.launch {
            runCatching {
                // Resolve spec directory: prefer .codespeak/specs, fall back to spec/
                val preferredDir = projectPath.child(".codespeak").child("specs")
                val specDir = if (
                    runCatching {
                        persistenceStore.isDirectory(preferredDir)
                    }.getOrElse { false }
                ) {
                    preferredDir
                } else {
                    projectPath.child("spec")
                }
                val fileName = "$sanitized.cs.md"
                val filePath = specDir.child(fileName)
                val template = "# ${specName.trim()}\n\n"
                persistenceStore.createDirectory(specDir)
                persistenceStore.writeFile(filePath, template.encodeToByteArray())
                onCreated()
            }.onFailure { e ->
                errorMessage = "Failed to create file: ${e.message}"
                isSaving = false
            }
        }
    }

    Column(
        modifier = Modifier
            .background(LocalDSColors.current.surfaceBase)
            .padding(0.dp),
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = DSSpacing.lg),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = LocalDSColors.current.agentCodeSpeak,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(DSSpacing.sm))
            Text(
                text = "New Spec",
                style = DSFont.settingsTitle,
                color = LocalDSColors.current.textPrimary,
            )
        }

        // 1dp divider
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(LocalDSColors.current.borderDefault),
        )

        // ── Body ────────────────────────────────────────────────────────────
        Column(
            verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(DSSpacing.lg),
        ) {
            Text(
                text = "File name",
                style = DSFont.sidebarItemSmall,
                color = LocalDSColors.current.textSecondary,
            )
            OutlinedTextField(
                value = specName,
                onValueChange = { specName = it; errorMessage = null },
                placeholder = {
                    Text(
                        "e.g. user-auth",
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
                            createSpec()
                            true
                        } else {
                            false
                        }
                    },
            )

            if (sanitized.isNotEmpty()) {
                Text(
                    text = "spec/$sanitized.cs.md",
                    style = DSFont.monoSmall,
                    color = LocalDSColors.current.textMuted,
                )
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    style = DSFont.sidebarItemSmall,
                    color = LocalDSColors.current.gitDeleted,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // 1dp divider
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(LocalDSColors.current.borderDefault),
        )

        // ── Footer ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DSSpacing.lg, vertical = DSSpacing.md),
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
                onClick = { createSpec() },
                enabled = canCreate,
                shape = RoundedCornerShape(DSRadius.md),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalDSColors.current.agentCodeSpeak,
                    contentColor = LocalDSColors.current.buttonPrimaryText,
                    disabledContainerColor = LocalDSColors.current.surfaceOverlay,
                    disabledContentColor = LocalDSColors.current.textDisabled,
                ),
                modifier = Modifier.width(90.dp),
            ) {
                if (isSaving) {
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

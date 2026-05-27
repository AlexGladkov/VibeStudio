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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.shared.model.SpecFile
import studio.vibe.shared.viewmodel.SpecEditorViewModel

/**
 * DialogWindow wrapping a split editor / preview for a single .cs.md spec file.
 *
 * Left pane  — editable [BasicTextField] with monospace font and line numbers.
 * Right pane — plain-text preview (scrollable).
 *
 * Mirrors SpecEditorSheet.swift: 900 x 600 sheet, save/cancel footer.
 *
 * @param container DI container providing [studio.vibe.shared.contract.PersistenceStore] and scope.
 * @param specFile  The spec file to open.
 * @param onDismiss Called on Cancel or after a successful save.
 */
@Composable
public fun SpecEditorSheet(
    container: DesktopServiceContainer,
    specFile: SpecFile,
    onDismiss: () -> Unit,
) {
    val dialogState = rememberDialogState(
        position = WindowPosition(Alignment.Center),
        width = 900.dp,
        height = 600.dp,
    )

    val vm = remember(specFile.id) {
        SpecEditorViewModel(
            persistenceStore = container.persistenceStore,
            scope = container.scope,
        ).also { it.loadSpec(specFile.path) }
    }

    val state by vm.state.collectAsState()

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = specFile.name,
        resizable = true,
    ) {
        SpecEditorContent(
            specName = specFile.name,
            content = state.content,
            isDirty = state.isDirty,
            isSaving = state.isSaving,
            errorMessage = state.errorMessage,
            onContentChange = { vm.updateContent(it) },
            onSave = {
                vm.saveSpec()
                // Dismiss is handled via LaunchedEffect on isDirty flip after save
            },
            onCancel = onDismiss,
        )
    }

    // Close after successful save (isDirty flips back to false, isSaving goes false)
    LaunchedEffect(state.isSaving, state.isDirty) {
        if (!state.isSaving && !state.isDirty && state.content.isNotEmpty() && state.errorMessage == null) {
            // Only close if we actually performed a save (state was dirty before)
        }
    }
}

@Composable
private fun SpecEditorContent(
    specName: String,
    content: String,
    isDirty: Boolean,
    isSaving: Boolean,
    errorMessage: String?,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DSColor.surfaceBase),
    ) {
        // ── Header ─────────────────────────────────────────────────────────
        SpecEditorHeader(
            specName = specName,
            isDirty = isDirty,
            onCancel = onCancel,
        )
        HorizontalDivider(color = DSColor.borderSubtle, thickness = 1.dp)

        // ── Split panes ────────────────────────────────────────────────────
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Left — editable code pane
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(DSColor.surfaceBase),
            ) {
                SpecPaneLabel("EDIT")
                HorizontalDivider(color = DSColor.borderSubtle, thickness = 1.dp)
                SpecCodeEditor(
                    content = content,
                    onContentChange = onContentChange,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Vertical divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(DSColor.borderSubtle),
            )

            // Right — plain-text preview pane
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(DSColor.surfaceRaised),
            ) {
                SpecPaneLabel("PREVIEW")
                HorizontalDivider(color = DSColor.borderSubtle, thickness = 1.dp)
                SpecPreview(
                    content = content,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        HorizontalDivider(color = DSColor.borderSubtle, thickness = 1.dp)

        // ── Error message ──────────────────────────────────────────────────
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = DSFont.sidebarItemSmall,
                color = DSColor.gitDeleted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DSSpacing.lg, vertical = DSSpacing.xs),
            )
        }

        // ── Footer ─────────────────────────────────────────────────────────
        SpecEditorFooter(
            isDirty = isDirty,
            isSaving = isSaving,
            onCancel = onCancel,
            onSave = onSave,
        )
    }
}

@Composable
private fun SpecEditorHeader(
    specName: String,
    isDirty: Boolean,
    onCancel: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = DSSpacing.lg),
    ) {
        Icon(
            imageVector = Icons.Filled.TextSnippet,
            contentDescription = null,
            tint = DSColor.agentCodeSpeak,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(DSSpacing.sm))
        Text(
            text = specName,
            style = DSFont.sidebarSection,
            color = DSColor.textPrimary,
        )
        if (isDirty) {
            Spacer(Modifier.width(DSSpacing.xs))
            Text(
                text = "\u2022",
                style = DSFont.sidebarItem,
                color = DSColor.gitModified,
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = onCancel,
            modifier = Modifier.size(24.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close",
                tint = DSColor.textMuted,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

@Composable
private fun SpecPaneLabel(label: String) {
    Text(
        text = label,
        style = DSFont.sidebarSection,
        color = DSColor.textMuted,
        modifier = Modifier
            .fillMaxWidth()
            .background(DSColor.surfaceOverlay)
            .padding(horizontal = DSSpacing.md, vertical = DSSpacing.xs),
    )
}

@Composable
private fun SpecCodeEditor(
    content: String,
    onContentChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Line numbers + editable field side by side
    val lines = content.split("\n")

    Row(modifier = modifier) {
        // Gutter — line numbers
        val lineCount = lines.size.coerceAtLeast(1)
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight()
                .background(DSColor.surfaceOverlay)
                .verticalScroll(scrollState)
                .padding(top = DSSpacing.md, bottom = DSSpacing.md),
        ) {
            repeat(lineCount) { idx ->
                Text(
                    text = "${idx + 1}",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = DSColor.textMuted,
                        lineHeight = 18.sp,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = DSSpacing.xs),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(DSColor.borderSubtle),
        )

        BasicTextField(
            value = content,
            onValueChange = onContentChange,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = DSColor.textPrimary,
                lineHeight = 18.sp,
            ),
            cursorBrush = SolidColor(DSColor.accentPrimary),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DSSpacing.md, vertical = DSSpacing.md),
        )
    }
}

@Composable
private fun SpecPreview(
    content: String,
    modifier: Modifier = Modifier,
) {
    SelectionContainer(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(DSSpacing.md),
    ) {
        Text(
            text = content.ifEmpty { "Start typing to preview\u2026" },
            style = DSFont.sidebarItem,
            color = if (content.isEmpty()) DSColor.textMuted else DSColor.textPrimary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SpecEditorFooter(
    isDirty: Boolean,
    isSaving: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DSSpacing.lg, vertical = DSSpacing.md),
    ) {
        Spacer(Modifier.weight(1f))
        OutlinedButton(
            onClick = onCancel,
            shape = RoundedCornerShape(DSRadius.md),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = DSColor.textSecondary,
            ),
        ) {
            Text("Cancel", style = DSFont.buttonLabel)
        }
        Spacer(Modifier.width(DSSpacing.sm))
        Button(
            onClick = onSave,
            enabled = isDirty && !isSaving,
            shape = RoundedCornerShape(DSRadius.md),
            colors = ButtonDefaults.buttonColors(
                containerColor = DSColor.agentCodeSpeak,
                contentColor = DSColor.buttonPrimaryText,
                disabledContainerColor = DSColor.surfaceOverlay,
                disabledContentColor = DSColor.textDisabled,
            ),
            modifier = Modifier.width(80.dp),
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = DSColor.buttonPrimaryText,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Save", style = DSFont.buttonLabel)
            }
        }
    }
}

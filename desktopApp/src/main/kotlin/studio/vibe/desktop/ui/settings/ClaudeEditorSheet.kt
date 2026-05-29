package studio.vibe.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import java.io.File

// ── ClaudeEditorSheet ─────────────────────────────────────────────────────────

/**
 * Modal sheet for editing `~/.claude/CLAUDE.md`.
 *
 * Mirrors ClaudeEditorSheet.swift (117 LOC).
 *
 * Loads the file on open, saves with directory creation, shows unsaved indicator.
 *
 * @param onDismiss Called when the sheet is closed.
 */
@Composable
public fun ClaudeEditorSheet(
    onDismiss: () -> Unit,
) {
    val dialogState = rememberDialogState(size = DpSize(680.dp, 520.dp))

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = "CLAUDE.md",
        resizable = false,
    ) {
        ClaudeEditorContent(onDismiss = onDismiss)
    }
}

// ── Content ───────────────────────────────────────────────────────────────────

@Composable
private fun ClaudeEditorContent(onDismiss: () -> Unit) {
    val homeDir = System.getProperty("user.home") ?: ""
    val claudeFile = remember { File("$homeDir/.claude/CLAUDE.md") }
    val displayPath = "~/.claude/CLAUDE.md"

    var content by remember { mutableStateOf("") }
    var savedContent by remember { mutableStateOf("") }
    var saveError by remember { mutableStateOf<String?>(null) }

    val hasUnsavedChanges = content != savedContent

    LaunchedEffect(Unit) {
        val text = runCatching { claudeFile.readText(Charsets.UTF_8) }.getOrElse { "" }
        content = text
        savedContent = text
    }

    fun save() {
        saveError = null
        runCatching {
            claudeFile.parentFile?.mkdirs()
            claudeFile.writeText(content, Charsets.UTF_8)
            savedContent = content
        }.onFailure { e ->
            saveError = "Ошибка: ${e.message}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalDSColors.current.surfaceBase)
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
    ) {
        // ── Toolbar ──────────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(LocalDSColors.current.surfaceRaised)
                .padding(horizontal = DSSpacing.lg, vertical = DSSpacing.sm),
        ) {
            Text(
                text = "CLAUDE.md",
                style = DSFont.settingsTitle,
                color = LocalDSColors.current.textPrimary,
                maxLines = 1,
            )
            Spacer(Modifier.width(DSSpacing.sm))
            Text(
                text = displayPath,
                style = DSFont.monoSmall,
                color = LocalDSColors.current.textMuted,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Закрыть",
                    tint = LocalDSColors.current.textMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        HorizontalDivider(color = LocalDSColors.current.borderDefault, thickness = 1.dp)

        // ── Editor ───────────────────────────────────────────────────────────
        MarkdownEditorView(
            text = content,
            onTextChange = { content = it },
            isEditable = true,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )

        HorizontalDivider(color = LocalDSColors.current.borderDefault, thickness = 1.dp)

        // ── Bottom bar ───────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(LocalDSColors.current.surfaceRaised)
                .padding(horizontal = DSSpacing.lg, vertical = DSSpacing.sm),
        ) {
            when {
                saveError != null -> Text(
                    text = saveError!!,
                    style = DSFont.sidebarItemSmall,
                    color = LocalDSColors.current.gitDeleted,
                )
                hasUnsavedChanges -> Text(
                    text = "Есть несохранённые изменения",
                    style = DSFont.sidebarItemSmall,
                    color = LocalDSColors.current.textMuted,
                )
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = ::save,
                enabled = hasUnsavedChanges,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(DSRadius.md),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalDSColors.current.accentPrimary,
                    contentColor = LocalDSColors.current.buttonPrimaryText,
                    disabledContainerColor = LocalDSColors.current.surfaceOverlay,
                    disabledContentColor = LocalDSColors.current.textDisabled,
                ),
            ) {
                Text("Сохранить", style = DSFont.buttonLabel)
            }
        }
    }
}

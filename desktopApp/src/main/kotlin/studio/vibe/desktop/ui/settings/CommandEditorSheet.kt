package studio.vibe.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import java.io.File

// ── CommandEditorSheet ────────────────────────────────────────────────────────

/**
 * Modal sheet for editing an existing command file or creating a new one.
 *
 * Mirrors CommandEditorSheet.swift (202 LOC).
 *
 * - [filePath] == null → create a new `.md` in `~/.claude/commands/`.
 *   Toolbar shows a filename TextField; filename validated as `[a-z0-9_\-]+`.
 * - [filePath] != null → overwrite in place on save.
 *   Toolbar shows the filename (without extension) + path subtitle.
 * - [onSaved] is called after a successful save so the caller can refresh its list.
 *
 * @param filePath Absolute path of the file to edit, or null to create a new command.
 * @param onSaved Called after a successful save.
 * @param onDismiss Called when the sheet is closed.
 */
@Composable
public fun CommandEditorSheet(
    filePath: String?,
    onSaved: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val dialogState = rememberDialogState(size = DpSize(680.dp, 520.dp))

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = if (filePath != null) File(filePath).nameWithoutExtension else "Новая команда",
        resizable = false,
    ) {
        CommandEditorContent(
            filePath = filePath,
            onSaved = onSaved,
            onDismiss = onDismiss,
        )
    }
}

// ── Content ───────────────────────────────────────────────────────────────────

/** Regex pattern for valid command filenames: lowercase letters, digits, dash, underscore. */
private val COMMAND_FILENAME_REGEX = Regex("^[a-z0-9_\\-]+$")

/** New command file template. */
private const val NEW_COMMAND_TEMPLATE = "# Название команды\n\nОписание что делает команда...\n"

@Composable
private fun CommandEditorContent(
    filePath: String?,
    onSaved: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val isNewFile = filePath == null
    val commandsDir = remember {
        File(System.getProperty("user.home") + "/.claude/commands")
    }

    var content by remember { mutableStateOf("") }
    var savedContent by remember { mutableStateOf("") }
    var saveError by remember { mutableStateOf<String?>(null) }
    var filename by remember { mutableStateOf("") }

    val hasUnsavedChanges = content != savedContent

    LaunchedEffect(Unit) {
        if (filePath != null) {
            val text = runCatching { File(filePath).readText(Charsets.UTF_8) }.getOrElse { "" }
            content = text
            savedContent = text
        } else {
            content = NEW_COMMAND_TEMPLATE
            savedContent = ""
        }
    }

    fun save() {
        saveError = null
        if (filePath != null) {
            runCatching {
                File(filePath).writeText(content, Charsets.UTF_8)
                savedContent = content
                onSaved?.invoke()
            }.onFailure { e ->
                saveError = "Ошибка: ${e.message}"
            }
        } else {
            val trimmed = filename.trim()
            if (trimmed.isEmpty()) {
                saveError = "Введите имя файла"
                return
            }
            if (!COMMAND_FILENAME_REGEX.matches(trimmed)) {
                saveError = "Допустимы только строчные латинские буквы, цифры, дефис и подчёркивание"
                return
            }
            val targetFile = File(commandsDir, "$trimmed.md")
            if (targetFile.exists()) {
                saveError = "Файл «$trimmed.md» уже существует"
                return
            }
            runCatching {
                commandsDir.mkdirs()
                targetFile.writeText(content, Charsets.UTF_8)
                savedContent = content
                onSaved?.invoke()
                onDismiss()
            }.onFailure { e ->
                saveError = "Ошибка: ${e.message}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DSColor.surfaceBase)
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
                .background(DSColor.surfaceRaised)
                .padding(horizontal = DSSpacing.lg, vertical = DSSpacing.sm),
        ) {
            if (isNewFile) {
                Text(
                    text = "Новая команда",
                    style = DSFont.settingsTitle,
                    color = DSColor.textPrimary,
                    maxLines = 1,
                )
                Spacer(Modifier.width(DSSpacing.sm))
                // Filename input
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = filename,
                        onValueChange = { filename = it },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = DSColor.textPrimary,
                        ),
                        cursorBrush = SolidColor(DSColor.accentPrimary),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .background(DSColor.surfaceOverlay, RoundedCornerShape(DSRadius.sm))
                                    .padding(horizontal = DSSpacing.sm, vertical = DSSpacing.xs)
                                    .width(160.dp),
                            ) {
                                if (filename.isEmpty()) {
                                    Text(
                                        text = "имя-файла",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = DSColor.textMuted,
                                        ),
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    Spacer(Modifier.width(DSSpacing.xs))
                    Text(
                        text = ".md",
                        style = DSFont.monoSmall,
                        color = DSColor.textMuted,
                    )
                }
            } else {
                // In the else branch isNewFile == false, so filePath != null (smart cast)
                @Suppress("USELESS_CAST")
                val existingPath = filePath as String
                val nameWithoutExt = File(existingPath).nameWithoutExtension
                val subtitle = existingPath.replace(System.getProperty("user.home") ?: "", "~")
                Text(
                    text = nameWithoutExt,
                    style = DSFont.settingsTitle,
                    color = DSColor.textPrimary,
                    maxLines = 1,
                )
                Spacer(Modifier.width(DSSpacing.sm))
                Text(
                    text = subtitle,
                    style = DSFont.monoSmall,
                    color = DSColor.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Закрыть",
                    tint = DSColor.textMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        HorizontalDivider(color = DSColor.borderDefault, thickness = 1.dp)

        // ── Editor ───────────────────────────────────────────────────────────
        MarkdownEditorView(
            text = content,
            onTextChange = { content = it },
            isEditable = true,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )

        HorizontalDivider(color = DSColor.borderDefault, thickness = 1.dp)

        // ── Bottom bar ───────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(DSColor.surfaceRaised)
                .padding(horizontal = DSSpacing.lg, vertical = DSSpacing.sm),
        ) {
            when {
                saveError != null -> Text(
                    text = saveError!!,
                    style = DSFont.sidebarItemSmall,
                    color = DSColor.gitDeleted,
                )
                hasUnsavedChanges -> Text(
                    text = "Есть несохранённые изменения",
                    style = DSFont.sidebarItemSmall,
                    color = DSColor.textMuted,
                )
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(DSRadius.md),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = DSColor.textSecondary,
                ),
            ) {
                Text("Закрыть", style = DSFont.buttonLabel)
            }
            Spacer(Modifier.width(DSSpacing.sm))
            Button(
                onClick = ::save,
                enabled = hasUnsavedChanges,
                shape = RoundedCornerShape(DSRadius.md),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DSColor.accentPrimary,
                    contentColor = DSColor.buttonPrimaryText,
                    disabledContainerColor = DSColor.surfaceOverlay,
                    disabledContentColor = DSColor.textDisabled,
                ),
            ) {
                Text("Сохранить", style = DSFont.buttonLabel)
            }
        }
    }
}

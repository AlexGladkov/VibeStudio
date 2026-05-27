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
import androidx.compose.material.icons.filled.Lock
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
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import java.io.File

// ── SkillInfo ─────────────────────────────────────────────────────────────────

/**
 * Parsed representation of a single skill directory from `~/.claude/skills/`.
 *
 * Mirrors the Swift `SkillInfo` struct.
 *
 * @param id Directory path used as stable identity.
 * @param directoryPath Absolute path of the skill directory.
 * @param skillFilePath Absolute path of the SKILL.md file inside the directory.
 * @param name Parsed `name:` from SKILL.md frontmatter.
 * @param description Parsed `description:` from SKILL.md frontmatter.
 * @param isUserInvocable Whether the skill declares `user_invocable: true`.
 * @param isWritable `false` for symlinks or read-only files (e.g. Homebrew-installed skills).
 */
public data class SkillInfo(
    val id: String,
    val directoryPath: String,
    val skillFilePath: String,
    val name: String,
    val description: String,
    val isUserInvocable: Boolean,
    val isWritable: Boolean,
)

// ── SkillViewerSheet ──────────────────────────────────────────────────────────

/**
 * Modal sheet for viewing (and optionally editing) a skill's SKILL.md.
 *
 * Mirrors SkillViewerSheet.swift (158 LOC).
 *
 * When [skill.isWritable] is `false` the editor is read-only and the save button is hidden.
 * The bottom bar shows "Скилл установлен через Homebrew..." for read-only skills.
 *
 * @param skill Skill to display.
 * @param onDismiss Called when the sheet is dismissed so the caller can refresh its skill list.
 */
@Composable
public fun SkillViewerSheet(
    skill: SkillInfo,
    onDismiss: () -> Unit,
) {
    val dialogState = rememberDialogState(size = DpSize(680.dp, 520.dp))

    DialogWindow(
        onCloseRequest = {
            onDismiss()
        },
        state = dialogState,
        title = skill.name.ifEmpty { File(skill.directoryPath).name },
        resizable = false,
    ) {
        SkillViewerContent(skill = skill, onDismiss = onDismiss)
    }
}

// ── Content ───────────────────────────────────────────────────────────────────

@Composable
private fun SkillViewerContent(
    skill: SkillInfo,
    onDismiss: () -> Unit,
) {
    var content by remember { mutableStateOf("") }
    var savedContent by remember { mutableStateOf("") }
    var saveError by remember { mutableStateOf<String?>(null) }

    val hasUnsavedChanges = content != savedContent

    val displayName = skill.name.ifEmpty { File(skill.directoryPath).name }

    LaunchedEffect(Unit) {
        val text = runCatching { File(skill.skillFilePath).readText(Charsets.UTF_8) }.getOrElse { "" }
        content = text
        savedContent = text
    }

    fun save() {
        saveError = null
        runCatching {
            File(skill.skillFilePath).writeText(content, Charsets.UTF_8)
            savedContent = content
            onDismiss()
        }.onFailure { e ->
            saveError = "Ошибка: ${e.message}"
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
            Text(
                text = displayName,
                style = DSFont.settingsTitle,
                color = DSColor.textPrimary,
                maxLines = 1,
            )
            if (!skill.isWritable) {
                Spacer(Modifier.width(DSSpacing.sm))
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = DSColor.textMuted,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(DSSpacing.xs))
                Text(
                    text = "Только чтение",
                    style = DSFont.sidebarItemSmall,
                    color = DSColor.textMuted,
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
            isEditable = skill.isWritable,
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
                !skill.isWritable -> Text(
                    text = "Скилл установлен через Homebrew, файл только для чтения",
                    style = DSFont.sidebarItemSmall,
                    color = DSColor.textMuted,
                )
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
            if (skill.isWritable) {
                Button(
                    onClick = ::save,
                    enabled = hasUnsavedChanges,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(DSRadius.md),
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
}

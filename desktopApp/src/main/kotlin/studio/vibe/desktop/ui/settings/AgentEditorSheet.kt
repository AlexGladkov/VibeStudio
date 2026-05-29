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
import androidx.compose.ui.text.style.TextOverflow
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

// ── AgentEditorSheet ──────────────────────────────────────────────────────────

/**
 * Modal sheet for editing an existing Claude subagent file or creating a new one.
 *
 * Mirrors AgentEditorSheet.swift (204 LOC).
 *
 * - [filePath] == null → create a new agent in `~/.claude/agents/`
 *   using the `name:` field from YAML frontmatter as filename.
 * - [filePath] != null → overwrite in place on save.
 * - [onDismiss] is called both on close and after a successful save so the caller
 *   can refresh its agent list.
 *
 * @param filePath Absolute path of the file to edit, or null to create a new agent.
 * @param onDismiss Called when the sheet is closed or a save completes.
 */
@Composable
public fun AgentEditorSheet(
    filePath: String?,
    onDismiss: () -> Unit,
) {
    val dialogState = rememberDialogState(size = DpSize(680.dp, 520.dp))

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = "Агент",
        resizable = false,
    ) {
        AgentEditorContent(
            filePath = filePath,
            onDismiss = onDismiss,
        )
    }
}

// ── Content ───────────────────────────────────────────────────────────────────

@Composable
private fun AgentEditorContent(
    filePath: String?,
    onDismiss: () -> Unit,
) {
    val agentsDir = remember {
        File(System.getProperty("user.home") + "/.claude/agents")
    }

    val newAgentTemplate = remember {
        """
        ---
        name: my-agent
        description:
        model: sonnet
        color: blue
        ---

        Ты — агент, выполняющий...
        """.trimIndent()
    }

    var content by remember { mutableStateOf("") }
    var savedContent by remember { mutableStateOf("") }
    var saveError by remember { mutableStateOf<String?>(null) }

    val hasUnsavedChanges = content != savedContent

    // Parsed agent name from frontmatter for the toolbar title
    val agentName = remember(content) {
        parseFrontmatterName(content).ifEmpty { "Новый агент" }
    }

    val agentSubtitle = remember(filePath) {
        filePath?.replace(System.getProperty("user.home") ?: "", "~") ?: "Новый файл"
    }

    LaunchedEffect(Unit) {
        if (filePath != null) {
            val text = runCatching { File(filePath).readText(Charsets.UTF_8) }.getOrElse { "" }
            content = text
            savedContent = text
        } else {
            content = newAgentTemplate
            savedContent = ""
        }
    }

    fun save() {
        saveError = null
        if (filePath != null) {
            runCatching {
                File(filePath).writeText(content, Charsets.UTF_8)
                savedContent = content
                onDismiss()
            }.onFailure { e ->
                saveError = "Ошибка: ${e.message}"
            }
        } else {
            val rawName = parseFrontmatterName(content)
            if (rawName.isEmpty()) {
                saveError = "Укажите поле name в frontmatter"
                return
            }
            val sanitized = sanitizeAgentName(rawName)
            if (sanitized.isEmpty()) {
                saveError = "Недопустимое имя агента: используйте латинские буквы, цифры и дефисы"
                return
            }
            val targetFile = File(agentsDir, "$sanitized.md")
            runCatching {
                agentsDir.mkdirs()
                targetFile.writeText(content, Charsets.UTF_8)
                savedContent = content
                onDismiss()
            }.onFailure { e ->
                saveError = "Ошибка: ${e.message}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalDSColors.current.surfaceBase)
            .onPreviewKeyEvent { event ->
                // Escape → close
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
                text = agentName,
                style = DSFont.settingsTitle,
                color = LocalDSColors.current.textPrimary,
                maxLines = 1,
            )
            Spacer(Modifier.width(DSSpacing.sm))
            Text(
                text = agentSubtitle,
                style = DSFont.monoSmall,
                color = LocalDSColors.current.textMuted,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(DSSpacing.sm))
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

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Extracts the value of the `name:` field from YAML frontmatter.
 *
 * Returns an empty string if not found.
 */
private fun parseFrontmatterName(text: String): String {
    val lines = text.lines()
    var inFrontmatter = false
    for (line in lines) {
        if (line.trim() == "---") {
            if (!inFrontmatter) {
                inFrontmatter = true
                continue
            } else {
                break
            }
        }
        if (inFrontmatter && line.startsWith("name:")) {
            return line.removePrefix("name:").trim()
        }
    }
    return ""
}

/**
 * Converts an agent name to a safe filename component.
 *
 * Rules: lowercase, spaces → dashes, only `[a-z0-9\-_]` allowed.
 */
private fun sanitizeAgentName(name: String): String =
    name.lowercase()
        .replace(' ', '-')
        .filter { it.isLetter() && it.code < 128 || it.isDigit() || it == '-' || it == '_' }

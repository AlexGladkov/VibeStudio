package studio.vibe.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.shared.model.AIAssistant
import java.io.File

/**
 * Settings pane for OpenAI Codex CLI.
 *
 * Mirrors Swift CodexSettingsPane.swift.
 *
 * Shows:
 * - Config file: ~/.codex/config.toml
 * - Memories list: ~/.codex/memories/ *.md
 * - Skills list: ~/.codex/skills/ directories
 * - API key setup instructions
 */
@Composable
fun CodexSettingsPane(
    container: DesktopServiceContainer,
    modifier: Modifier = Modifier,
) {
    val homeDir = System.getProperty("user.home") ?: ""
    val configPath = remember { "$homeDir/.codex/config.toml" }
    val memoriesDir = remember { File("$homeDir/.codex/memories") }
    val skillsDir = remember { File("$homeDir/.codex/skills") }

    var configExists by remember { mutableStateOf(false) }
    var memoryFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var skillDirs by remember { mutableStateOf<List<File>>(emptyList()) }

    LaunchedEffect(Unit) {
        configExists = File(configPath).exists()
        memoryFiles = if (memoriesDir.exists() && memoriesDir.isDirectory) {
            memoriesDir.listFiles { f -> f.extension == "md" && !f.name.startsWith(".") }
                ?.sortedBy { it.name }
                ?: emptyList()
        } else {
            emptyList()
        }
        skillDirs = if (skillsDir.exists() && skillsDir.isDirectory) {
            skillsDir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
                ?.sortedBy { it.name }
                ?: emptyList()
        } else {
            emptyList()
        }
    }

    LlmBasicPane(
        assistant = AIAssistant.CODEX,
        modifier = modifier,
        extraContent = {
            // -- Config file ---------------------------------------------

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
            ) {
                Text(
                    text = "Config",
                    style = DSFont.buttonLabel,
                    color = DSColor.textSecondary,
                )

                SettingsCard {
                    Column(
                        modifier = Modifier.padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
                    ) {
                        Text(
                            text = configPath.replace(homeDir, "~"),
                            style = DSFont.monoPath,
                            color = if (configExists) DSColor.textPrimary else DSColor.textMuted,
                            maxLines = 1,
                        )
                        if (!configExists) {
                            Text(
                                text = "not found — create via CLI: codex",
                                style = DSFont.sidebarItemSmall,
                                color = DSColor.textMuted,
                            )
                        }
                    }
                }
            }

            // -- Memories ------------------------------------------------

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
            ) {
                SettingsSectionHeader(title = "Memories")

                if (memoryFiles.isEmpty()) {
                    SettingsEmptyState(text = "No memory files in ~/.codex/memories/")
                } else {
                    SettingsCard {
                        Column {
                            memoryFiles.forEachIndexed { index, file ->
                                SettingsItemRow(
                                    name = file.nameWithoutExtension,
                                    subtitle = file.name,
                                    showDelete = false,
                                )
                                if (index < memoryFiles.lastIndex) {
                                    SettingsListDivider()
                                }
                            }
                        }
                    }
                }
            }

            // -- Skills --------------------------------------------------

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
            ) {
                SettingsSectionHeader(title = "Skills")

                if (skillDirs.isEmpty()) {
                    SettingsEmptyState(text = "No skills in ~/.codex/skills/")
                } else {
                    SettingsCard {
                        Column {
                            skillDirs.forEachIndexed { index, dir ->
                                SettingsItemRow(
                                    name = dir.name,
                                    showDelete = false,
                                )
                                if (index < skillDirs.lastIndex) {
                                    SettingsListDivider()
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

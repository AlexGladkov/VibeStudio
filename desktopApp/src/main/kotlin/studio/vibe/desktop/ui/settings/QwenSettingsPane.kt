package studio.vibe.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.shared.model.AIAssistant
import java.io.File

/**
 * Settings pane for Qwen Code CLI (Alibaba).
 *
 * Mirrors Swift QwenSettingsPane.swift.
 *
 * Shows:
 * - Global config: ~/.qwen/QWEN.md
 * - Subagents: ~/.qwen/agents/ *.md
 * - API key (DashScope) setup instructions
 */
@Composable
fun QwenSettingsPane(
    modifier: Modifier = Modifier,
) {
    val homeDir = System.getProperty("user.home") ?: ""
    val configPath = remember { "$homeDir/.qwen/QWEN.md" }
    val agentsDir = remember { File("$homeDir/.qwen/agents") }

    var configExists by remember { mutableStateOf(false) }
    var agentFiles by remember { mutableStateOf<List<File>>(emptyList()) }

    LaunchedEffect(Unit) {
        configExists = File(configPath).exists()
        agentFiles = if (agentsDir.exists() && agentsDir.isDirectory) {
            agentsDir.listFiles { f -> f.extension == "md" && !f.name.startsWith(".") }
                ?.sortedBy { it.name }
                ?: emptyList()
        } else {
            emptyList()
        }
    }

    LlmBasicPane(
        assistant = AIAssistant.QWEN_CODE,
        modifier = modifier,
        extraContent = {
            // -- Global config -------------------------------------------

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
            ) {
                Text(
                    text = "Global config",
                    style = DSFont.buttonLabel,
                    color = LocalDSColors.current.textSecondary,
                )

                SettingsCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(DSSpacing.xs),
                    ) {
                        Text(
                            text = configPath.replace(homeDir, "~"),
                            style = DSFont.monoPath,
                            color = if (configExists) LocalDSColors.current.textPrimary else LocalDSColors.current.textMuted,
                            maxLines = 1,
                        )
                        if (!configExists) {
                            Text(
                                text = "not found",
                                style = DSFont.sidebarItemSmall,
                                color = LocalDSColors.current.textMuted,
                            )
                        }
                    }
                }
            }

            // -- Subagents ------------------------------------------------

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
            ) {
                SettingsSectionHeader(title = "Subagents")

                if (agentFiles.isEmpty()) {
                    SettingsEmptyState(text = "No agents in ~/.qwen/agents/")
                } else {
                    SettingsCard {
                        Column {
                            agentFiles.forEachIndexed { index, file ->
                                SettingsItemRow(
                                    name = file.nameWithoutExtension,
                                    subtitle = file.name,
                                    showDelete = false,
                                )
                                if (index < agentFiles.lastIndex) {
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

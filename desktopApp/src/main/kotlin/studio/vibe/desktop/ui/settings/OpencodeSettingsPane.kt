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
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.shared.model.AIAssistant
import java.io.File

/**
 * Settings pane for OpenCode AI coding assistant.
 *
 * Mirrors Swift OpencodeSettingsPane.swift.
 *
 * Shows:
 * - Config directory: ~/.config/opencode/
 * - TypeScript plugins: ~/.config/opencode/plugins/ *.ts
 * - Auth info (managed via `opencode providers` CLI)
 */
@Composable
fun OpencodeSettingsPane(
    container: DesktopServiceContainer,
    modifier: Modifier = Modifier,
) {
    val homeDir = System.getProperty("user.home") ?: ""
    val configDirPath = remember { "$homeDir/.config/opencode" }
    val pluginsDir = remember { File("$homeDir/.config/opencode/plugins") }

    var pluginFiles by remember { mutableStateOf<List<File>>(emptyList()) }

    LaunchedEffect(Unit) {
        pluginFiles = if (pluginsDir.exists() && pluginsDir.isDirectory) {
            pluginsDir.listFiles { f -> f.extension == "ts" && !f.name.startsWith(".") }
                ?.sortedBy { it.name }
                ?: emptyList()
        } else {
            emptyList()
        }
    }

    LlmBasicPane(
        assistant = AIAssistant.OPENCODE,
        modifier = modifier,
        extraContent = {
            // -- Config directory ----------------------------------------

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
            ) {
                Text(
                    text = "Config directory",
                    style = DSFont.buttonLabel,
                    color = DSColor.textSecondary,
                )

                SettingsCard {
                    Text(
                        text = configDirPath.replace(homeDir, "~"),
                        style = DSFont.monoPath,
                        color = if (File(configDirPath).exists()) DSColor.textPrimary else DSColor.textMuted,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
                    )
                }
            }

            // -- Plugins -------------------------------------------------

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
            ) {
                SettingsSectionHeader(title = "Plugins")

                if (pluginFiles.isEmpty()) {
                    SettingsEmptyState(text = "No TypeScript plugins in ~/.config/opencode/plugins/")
                } else {
                    SettingsCard {
                        Column {
                            pluginFiles.forEachIndexed { index, file ->
                                SettingsItemRow(
                                    name = file.nameWithoutExtension,
                                    subtitle = file.name,
                                    showDelete = false,
                                )
                                if (index < pluginFiles.lastIndex) {
                                    SettingsListDivider()
                                }
                            }
                        }
                    }
                }
            }

            // -- Providers info ------------------------------------------

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
            ) {
                Text(
                    text = "Authentication",
                    style = DSFont.buttonLabel,
                    color = DSColor.textSecondary,
                )

                SettingsCard {
                    Text(
                        text = "Providers and API keys are managed via CLI: opencode providers",
                        style = DSFont.bodySmall,
                        color = DSColor.textSecondary,
                        modifier = Modifier.padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
                    )
                }
            }
        },
    )
}

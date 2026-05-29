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
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.shared.model.AIAssistant
import java.io.File

/**
 * Settings pane for Google Gemini CLI.
 *
 * Mirrors Swift GeminiSettingsPane.swift.
 *
 * Shows ~/.gemini/settings.json with existence check
 * and API key environment variable instructions.
 */
@Composable
fun GeminiSettingsPane(
    container: DesktopServiceContainer,
    modifier: Modifier = Modifier,
) {
    val homeDir = System.getProperty("user.home") ?: ""
    val settingsPath = remember { "$homeDir/.gemini/settings.json" }
    var configExists by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        configExists = File(settingsPath).exists()
    }

    LlmBasicPane(
        assistant = AIAssistant.GEMINI,
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
                            text = settingsPath.replace(homeDir, "~"),
                            style = DSFont.monoPath,
                            color = if (configExists) LocalDSColors.current.textPrimary else LocalDSColors.current.textMuted,
                            maxLines = 1,
                        )
                        if (!configExists) {
                            Text(
                                text = "not found — run `gemini` to auto-create on first launch",
                                style = DSFont.sidebarItemSmall,
                                color = LocalDSColors.current.textMuted,
                            )
                        } else {
                            Text(
                                text = "Available keys: theme, sandbox, checkpointing, preferredEditor, contextWindowCompression, telemetry, mcpServers, extensions",
                                style = DSFont.sidebarItemSmall,
                                color = LocalDSColors.current.textMuted,
                            )
                        }
                    }
                }
            }
        },
    )
}

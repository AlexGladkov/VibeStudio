package studio.vibe.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSSpacing
import java.io.File

/**
 * Settings pane for Claude.
 *
 * Mirrors Swift ClaudeSettingsPane.swift.
 *
 * Shows:
 * - Launch options (--dangerously-skip-permissions toggle)
 * - Global config file path (~/.claude/CLAUDE.md) with open-in-Finder action
 * - List of subagents from ~/.claude/agents/
 * - Installation hint
 */
@Composable
fun ClaudeSettingsPane(
    container: DesktopServiceContainer,
    modifier: Modifier = Modifier,
) {
    val prefs = container.generalPreferences

    var skipPermissions by remember { mutableStateOf(prefs.claudeSkipPermissions) }

    // File-system state
    val claudeConfigPath = remember {
        System.getProperty("user.home") + "/.claude/CLAUDE.md"
    }
    val agentsDir = remember {
        File(System.getProperty("user.home") + "/.claude/agents")
    }
    var agentFiles by remember { mutableStateOf<List<File>>(emptyList()) }

    LaunchedEffect(Unit) {
        agentFiles = if (agentsDir.exists() && agentsDir.isDirectory) {
            agentsDir.listFiles { f -> f.extension == "md" }
                ?.sortedBy { it.name }
                ?: emptyList()
        } else {
            emptyList()
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(DSSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(DSSpacing.xl),
    ) {
        SettingsPaneTitle(title = "Claude")

        // -- Launch options --------------------------------------------------

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
        ) {
            Text(
                text = "Launch",
                style = DSFont.buttonLabel,
                color = LocalDSColors.current.textSecondary,
            )

            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "--dangerously-skip-permissions",
                            style = DSFont.monoPath,
                            color = LocalDSColors.current.textPrimary,
                        )
                        Text(
                            text = "Launch Claude without tool-use confirmation prompts",
                            style = DSFont.sidebarItemSmall,
                            color = LocalDSColors.current.textMuted,
                        )
                    }

                    Spacer(Modifier.width(DSSpacing.sm))

                    Checkbox(
                        checked = skipPermissions,
                        onCheckedChange = { checked ->
                            skipPermissions = checked
                            prefs.claudeSkipPermissions = checked
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = LocalDSColors.current.agentClaude,
                            uncheckedColor = LocalDSColors.current.textMuted,
                            checkmarkColor = Color.White,
                        ),
                    )
                }
            }
        }

        // -- Global config file ----------------------------------------------

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
                        text = claudeConfigPath.replace(System.getProperty("user.home") ?: "", "~"),
                        style = DSFont.monoPath,
                        color = if (File(claudeConfigPath).exists()) LocalDSColors.current.textPrimary else LocalDSColors.current.textMuted,
                        maxLines = 1,
                    )

                    Row {
                        Text(
                            text = "Open via: Finder → Go → Go to Folder → ~/.claude/",
                            style = DSFont.sidebarItemSmall,
                            color = LocalDSColors.current.textMuted,
                        )
                    }
                }
            }
        }

        // -- Subagents list --------------------------------------------------

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
        ) {
            SettingsSectionHeader(title = "Subagents")

            if (agentFiles.isEmpty()) {
                SettingsEmptyState(text = "No agents found in ~/.claude/agents/")
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

        // -- Authentication --------------------------------------------------

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
        ) {
            Text(
                text = "Authentication",
                style = DSFont.buttonLabel,
                color = LocalDSColors.current.textSecondary,
            )

            SettingsCard {
                Text(
                    text = "After installation, run `claude login` to authenticate with your Anthropic account.",
                    style = DSFont.bodySmall,
                    color = LocalDSColors.current.textSecondary,
                    modifier = Modifier.padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
                )
            }
        }

        // -- Installation ----------------------------------------------------

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
        ) {
            Text(
                text = "Installation",
                style = DSFont.buttonLabel,
                color = LocalDSColors.current.textSecondary,
            )

            SettingsMonoBlock(
                text = "npm install -g @anthropic-ai/claude-code",
            )

            Text(
                text = "Requires: Node.js 18+",
                style = DSFont.sidebarItemSmall,
                color = LocalDSColors.current.textMuted,
            )
        }

        Spacer(Modifier.height(DSSpacing.xl))
    }
}

package studio.vibe.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.shared.model.CodeSpeakCommand
import java.io.File

/**
 * Settings pane for CodeSpeak spec-driven AI coding tool.
 *
 * Mirrors Swift CodeSpeakSettingsPane.swift.
 *
 * Shows:
 * - Status (installed / authenticated)
 * - Behaviour preferences (auto-build, build-on-open, auto-open panel, default command)
 * - Display preferences (show failing only)
 * - Notifications (notify on complete)
 * - API key info
 * - Installation instructions (when not installed)
 */
@Composable
fun CodeSpeakSettingsPane(
    container: DesktopServiceContainer,
    modifier: Modifier = Modifier,
) {
    val prefs = container.codeSpeakPreferences

    // Installation status
    var isInstalled by remember { mutableStateOf(false) }
    var hasAPIKey by remember { mutableStateOf(false) }

    // Behaviour prefs
    var autoBuildOnSave by remember { mutableStateOf(prefs.autoBuildOnSave) }
    var buildOnProjectOpen by remember { mutableStateOf(prefs.buildOnProjectOpen) }
    var autoOpenBuildPanel by remember { mutableStateOf(prefs.autoOpenBuildPanel) }
    var defaultCommand by remember { mutableStateOf(prefs.defaultCommand) }

    // Display prefs
    var showFailingOnly by remember { mutableStateOf(prefs.showFailingOnly) }

    // Notification prefs
    var notifyOnComplete by remember { mutableStateOf(prefs.notifyOnComplete) }

    LaunchedEffect(Unit) {
        // Check if codespeak binary is on PATH
        isInstalled = runCatching {
            val result = ProcessBuilder("which", "codespeak")
                .start()
                .waitFor()
            result == 0
        }.getOrDefault(false)

        // Check for codespeak token file or ANTHROPIC_API_KEY
        hasAPIKey = runCatching {
            val tokenFile = File("${System.getProperty("user.home")}/.codespeak/token.json")
            if (tokenFile.exists()) {
                true
            } else {
                System.getenv("ANTHROPIC_API_KEY")?.isNotEmpty() == true
            }
        }.getOrDefault(false)
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(DSSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(DSSpacing.xl),
    ) {
        SettingsPaneTitle(title = "CodeSpeak")

        // -- Status ----------------------------------------------------------

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
        ) {
            SettingsSectionHeader(title = "Status")

            SettingsCard {
                Column {
                    StatusRow(
                        ok = isInstalled,
                        okText = "codespeak installed",
                        failText = "codespeak not found",
                    )
                    SettingsListDivider()
                    StatusRow(
                        ok = hasAPIKey,
                        okText = "Authenticated",
                        failText = "Not authenticated — run codespeak login",
                        failColor = DSColor.gitModified,
                    )
                }
            }
        }

        // -- Behaviour -------------------------------------------------------

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
        ) {
            SettingsSectionHeader(title = "Behaviour")

            SettingsCard {
                Column {
                    SettingsToggleRow(
                        title = "Auto-build on save",
                        description = "Run `codespeak build` automatically after saving a spec file",
                        checked = autoBuildOnSave,
                        onCheckedChange = { value ->
                            autoBuildOnSave = value
                            prefs.autoBuildOnSave = value
                        },
                    )

                    SettingsListDivider()

                    SettingsToggleRow(
                        title = "Build on project open",
                        description = "Run `codespeak build` when switching to a CodeSpeak project",
                        checked = buildOnProjectOpen,
                        onCheckedChange = { value ->
                            buildOnProjectOpen = value
                            prefs.buildOnProjectOpen = value
                        },
                    )

                    SettingsListDivider()

                    SettingsToggleRow(
                        title = "Auto-open build panel",
                        description = "Open the build panel automatically when a command starts",
                        checked = autoOpenBuildPanel,
                        onCheckedChange = { value ->
                            autoOpenBuildPanel = value
                            prefs.autoOpenBuildPanel = value
                        },
                    )

                    SettingsListDivider()

                    // Default command picker
                    DefaultCommandRow(
                        selectedCommand = defaultCommand,
                        onCommandSelected = { cmd ->
                            defaultCommand = cmd
                            prefs.defaultCommand = cmd
                        },
                    )
                }
            }
        }

        // -- Display ---------------------------------------------------------

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
        ) {
            SettingsSectionHeader(title = "Display")

            SettingsCard {
                SettingsToggleRow(
                    title = "Show failing specs only",
                    description = "Filter the spec list to show only specs with failing status",
                    checked = showFailingOnly,
                    onCheckedChange = { value ->
                        showFailingOnly = value
                        prefs.showFailingOnly = value
                    },
                )
            }
        }

        // -- Notifications ----------------------------------------------------

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
        ) {
            SettingsSectionHeader(title = "Notifications")

            SettingsCard {
                SettingsToggleRow(
                    title = "Notify on complete",
                    description = "Show a system notification when a command finishes",
                    checked = notifyOnComplete,
                    onCheckedChange = { value ->
                        notifyOnComplete = value
                        prefs.notifyOnComplete = value
                    },
                )
            }
        }

        // -- API Key ----------------------------------------------------------

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
        ) {
            SettingsSectionHeader(title = "API Key")

            SettingsCard {
                Text(
                    text = "Authenticate via `codespeak login` (OAuth) or set ANTHROPIC_API_KEY in Settings → Claude.",
                    style = DSFont.buttonLabel,
                    color = DSColor.textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
                )
            }
        }

        // -- Installation (shown only when not installed) ---------------------

        if (!isInstalled) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
            ) {
                SettingsSectionHeader(title = "Installation")

                SettingsCard {
                    Text(
                        text = "1. Install uv: curl -LsSf https://astral.sh/uv/install.sh | sh\n" +
                            "2. uv tool install codespeak-cli\n" +
                            "3. codespeak init  (in your project)",
                        style = DSFont.monoSmall,
                        color = DSColor.textPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
                    )
                }
            }
        }

        Spacer(Modifier.height(DSSpacing.xl))
    }
}

// -- Private helper composables -------------------------------------------------

@Composable
private fun StatusRow(
    ok: Boolean,
    okText: String,
    failText: String,
    failColor: Color = DSColor.gitDeleted,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (ok) DSColor.indicatorRunning else failColor,
                    shape = CircleShape,
                ),
        )
        Spacer(Modifier.width(DSSpacing.sm))
        Text(
            text = if (ok) okText else failText,
            style = DSFont.buttonLabel,
            color = DSColor.textPrimary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultCommandRow(
    selectedCommand: CodeSpeakCommand,
    onCommandSelected: (CodeSpeakCommand) -> Unit,
) {
    val validCommands = CodeSpeakCommand.entries.filter { !it.requiresInput }
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Default command",
                style = DSFont.buttonLabel,
                color = DSColor.textPrimary,
            )
            Text(
                text = "Command executed by the run button",
                style = DSFont.sidebarItemSmall,
                color = DSColor.textSecondary,
            )
        }

        Spacer(Modifier.width(DSSpacing.sm))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            TextField(
                value = selectedCommand.displayName,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .width(100.dp),
                textStyle = DSFont.bodySmall.copy(color = DSColor.textPrimary),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = DSColor.surfaceOverlay,
                    unfocusedContainerColor = DSColor.surfaceOverlay,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                validCommands.forEach { cmd ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = cmd.displayName,
                                style = DSFont.bodySmall,
                                color = DSColor.textPrimary,
                            )
                        },
                        onClick = {
                            onCommandSelected(cmd)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

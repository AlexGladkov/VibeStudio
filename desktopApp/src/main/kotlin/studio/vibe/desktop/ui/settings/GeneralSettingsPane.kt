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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import studio.vibe.desktop.ui.theme.DSSpacing

/**
 * General / Appearance settings pane.
 *
 * Mirrors Swift GeneralSettingsPane.swift.
 * Provides: theme/appearance, terminal font size, confirm-tab-close toggle,
 * and Claude skip-permissions toggle.
 */
@Composable
fun GeneralSettingsPane(
    container: DesktopServiceContainer,
    modifier: Modifier = Modifier,
) {
    val prefs = container.generalPreferences

    var confirmTabClose by remember { mutableStateOf(prefs.confirmTabClose) }
    var skipPermissions by remember { mutableStateOf(prefs.claudeSkipPermissions) }
    var fontSize by remember { mutableStateOf(prefs.terminalFontSize.toFloat()) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(DSSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(DSSpacing.xl),
    ) {
        SettingsPaneTitle(title = "General")

        // -- Terminal font size ----------------------------------------------

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
        ) {
            Text(
                text = "Terminal",
                style = DSFont.buttonLabel,
                color = DSColor.textSecondary,
            )

            SettingsCard {
                Column(modifier = Modifier.padding(DSSpacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Font size",
                            style = DSFont.sidebarItem,
                            color = DSColor.textPrimary,
                            modifier = Modifier.width(170.dp),
                        )
                        Text(
                            text = "${fontSize.toInt()} pt",
                            style = DSFont.bodySmall,
                            color = DSColor.textSecondary,
                        )
                    }

                    Slider(
                        value = fontSize,
                        onValueChange = { fontSize = it },
                        onValueChangeFinished = {
                            prefs.terminalFontSize = fontSize.toInt()
                        },
                        valueRange = 9f..24f,
                        steps = 14,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = DSColor.accentPrimary,
                            activeTrackColor = DSColor.accentPrimary,
                            inactiveTrackColor = DSColor.surfaceOverlay,
                        ),
                    )
                }
            }
        }

        // -- Behaviour ------------------------------------------------------

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
        ) {
            Text(
                text = "Behaviour",
                style = DSFont.buttonLabel,
                color = DSColor.textSecondary,
            )

            SettingsCard {
                Column {
                    // Confirm tab close
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .padding(horizontal = DSSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Confirm tab close",
                            style = DSFont.sidebarItem,
                            color = DSColor.textPrimary,
                            modifier = Modifier.weight(1f),
                        )

                        Text(
                            text = "Ask before closing tabs",
                            style = DSFont.sidebarItemSmall,
                            color = DSColor.textMuted,
                        )

                        Spacer(Modifier.width(DSSpacing.sm))

                        Checkbox(
                            checked = confirmTabClose,
                            onCheckedChange = { checked ->
                                confirmTabClose = checked
                                prefs.confirmTabClose = checked
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = DSColor.accentPrimary,
                                uncheckedColor = DSColor.textMuted,
                                checkmarkColor = Color.White,
                            ),
                        )
                    }

                    SettingsListDivider()

                    // Skip permissions (Claude)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .padding(horizontal = DSSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "--dangerously-skip-permissions",
                            style = DSFont.monoPath,
                            color = DSColor.textPrimary,
                            modifier = Modifier.weight(1f),
                        )

                        Text(
                            text = "Claude",
                            style = DSFont.sidebarItemSmall,
                            color = DSColor.agentClaude,
                        )

                        Spacer(Modifier.width(DSSpacing.sm))

                        Checkbox(
                            checked = skipPermissions,
                            onCheckedChange = { checked ->
                                skipPermissions = checked
                                prefs.claudeSkipPermissions = checked
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = DSColor.agentClaude,
                                uncheckedColor = DSColor.textMuted,
                                checkmarkColor = Color.White,
                            ),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(DSSpacing.xl))
    }
}

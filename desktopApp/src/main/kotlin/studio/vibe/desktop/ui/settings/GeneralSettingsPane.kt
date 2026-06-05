package studio.vibe.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import studio.vibe.shared.preferences.GeneralPreferences
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.shared.preferences.AppTheme

/**
 * General / Appearance settings pane.
 *
 * Mirrors Swift GeneralSettingsPane.swift.
 * Provides: theme/appearance, terminal font size, confirm-tab-close toggle,
 * and Claude skip-permissions toggle.
 *
 * All preference values are sourced from reactive StateFlows on
 * [GeneralPreferences]; mutations propagate to every observer that
 * collects those flows.
 */
@Composable
fun GeneralSettingsPane(
    preferences: GeneralPreferences,
    modifier: Modifier = Modifier,
) {
    val prefs = preferences
    val colors = LocalDSColors.current

    val confirmTabClose by prefs.confirmTabCloseFlow.collectAsState()
    val skipPermissions by prefs.claudeSkipPermissionsFlow.collectAsState()
    val persistedFontSize by prefs.terminalFontSizeFlow.collectAsState()
    val selectedTheme by prefs.themeFlow.collectAsState()

    // Slider holds a transient in-drag value; commits to prefs on release.
    var fontSize by remember { mutableStateOf(persistedFontSize.toFloat()) }
    LaunchedEffect(persistedFontSize) { fontSize = persistedFontSize.toFloat() }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(DSSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(DSSpacing.xl),
    ) {
        SettingsPaneTitle(title = "Внешний вид")

        // -- Appearance -------------------------------------------------------

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
        ) {
            Text(
                text = "Внешний вид",
                style = DSFont.buttonLabel,
                color = colors.textSecondary,
            )

            SettingsCard {
                Column(modifier = Modifier.padding(DSSpacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Тема",
                            style = DSFont.sidebarItem,
                            color = colors.textPrimary,
                            modifier = Modifier.width(170.dp),
                        )

                        ThemeSegmentedControl(
                            selected = selectedTheme,
                            onSelect = { theme -> prefs.theme = theme },
                        )
                    }
                }
            }
        }

        // -- Terminal font size ----------------------------------------------

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
        ) {
            Text(
                text = "Терминал",
                style = DSFont.buttonLabel,
                color = colors.textSecondary,
            )

            SettingsCard {
                Column(modifier = Modifier.padding(DSSpacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Шрифт терминала",
                            style = DSFont.sidebarItem,
                            color = colors.textPrimary,
                            modifier = Modifier.width(170.dp),
                        )
                        Text(
                            text = "${fontSize.toInt()} pt",
                            style = DSFont.bodySmall,
                            color = colors.textSecondary,
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
                            thumbColor = colors.accentPrimary,
                            activeTrackColor = colors.accentPrimary,
                            inactiveTrackColor = colors.surfaceOverlay,
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
                text = "Поведение",
                style = DSFont.buttonLabel,
                color = colors.textSecondary,
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
                            text = "Подтверждать закрытие",
                            style = DSFont.sidebarItem,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )

                        Text(
                            text = "Спрашивать перед закрытием вкладок",
                            style = DSFont.sidebarItemSmall,
                            color = colors.textMuted,
                        )

                        Spacer(Modifier.width(DSSpacing.sm))

                        Checkbox(
                            checked = confirmTabClose,
                            onCheckedChange = { checked -> prefs.confirmTabClose = checked },
                            colors = CheckboxDefaults.colors(
                                checkedColor = colors.accentPrimary,
                                uncheckedColor = colors.textMuted,
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
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )

                        Text(
                            text = "Claude",
                            style = DSFont.sidebarItemSmall,
                            color = colors.agentClaude,
                        )

                        Spacer(Modifier.width(DSSpacing.sm))

                        Checkbox(
                            checked = skipPermissions,
                            onCheckedChange = { checked -> prefs.claudeSkipPermissions = checked },
                            colors = CheckboxDefaults.colors(
                                checkedColor = colors.agentClaude,
                                uncheckedColor = colors.textMuted,
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

// ── ThemeSegmentedControl ─────────────────────────────────────────────────────

/**
 * A three-segment control for selecting [AppTheme].
 *
 * Renders "Light", "Dark", and "System" as pill-shaped button segments.
 * The active segment is filled with [accentPrimarySubtle]; inactive segments
 * are transparent with a [borderSubtle] outline.
 */
@Composable
private fun ThemeSegmentedControl(
    selected: AppTheme,
    onSelect: (AppTheme) -> Unit,
) {
    val colors = LocalDSColors.current
    val segments = listOf(
        AppTheme.SYSTEM to "System",
        AppTheme.DARK   to "Dark",
        AppTheme.LIGHT  to "Light",
    )
    val shape = RoundedCornerShape(DSRadius.sm)

    Row(
        modifier = Modifier
            .clip(shape)
            .border(1.dp, colors.borderDefault, shape),
        horizontalArrangement = Arrangement.Start,
    ) {
        segments.forEachIndexed { index, (theme, label) ->
            val isSelected = selected == theme
            val segmentBg = if (isSelected) colors.accentPrimarySubtle else Color.Transparent
            val segmentText = if (isSelected) colors.accentPrimary else colors.textSecondary

            // Internal divider between segments (skip before the first)
            if (index > 0) {
                Spacer(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(colors.borderDefault),
                )
            }

            Text(
                text = label,
                style = DSFont.smallButtonLabel,
                color = segmentText,
                modifier = Modifier
                    .background(segmentBg)
                    .clickable { onSelect(theme) }
                    .padding(horizontal = DSSpacing.md, vertical = DSSpacing.xs),
            )
        }
    }
}

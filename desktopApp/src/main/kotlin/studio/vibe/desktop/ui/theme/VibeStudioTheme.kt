package studio.vibe.desktop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── DSColors — immutable color palette snapshot ───────────────────────────────

/**
 * Immutable snapshot of all design-system color tokens.
 *
 * The active instance is provided via [LocalDSColors] and should always be
 * accessed through the CompositionLocal rather than through [DSColor.dark] or
 * [DSColor.light] directly in Composables.
 *
 * Example:
 * ```kotlin
 * val colors = LocalDSColors.current
 * Text(color = colors.textPrimary, ...)
 * ```
 */
@Immutable
public data class DSColors(
    // Surfaces
    val surfaceBase: Color,
    val surfaceRaised: Color,
    val surfaceOverlay: Color,
    val surfaceTabBar: Color,
    val surfaceTabActive: Color,
    val surfaceTabInactive: Color,
    val surfaceTabHover: Color,
    val surfaceInput: Color,
    val surfaceSelection: Color,
    val surfaceToolbar: Color,

    // Text
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textInverse: Color,
    val textDisabled: Color,
    val textGhost: Color,

    // Borders
    val borderDefault: Color,
    val borderSubtle: Color,
    val borderFocus: Color,

    // Accent
    val accentPrimary: Color,
    val accentPrimaryHover: Color,
    val accentSecondary: Color,
    val accentPrimarySubtle: Color,

    // Git
    val gitModified: Color,
    val gitAdded: Color,
    val gitDeleted: Color,
    val gitUntracked: Color,
    val gitConflicted: Color,
    val gitRenamed: Color,

    // Diff
    val diffAddedBg: Color,
    val diffDeletedBg: Color,
    val diffGutter: Color,

    // Activity indicators
    val indicatorIdle: Color,
    val indicatorRunning: Color,
    val indicatorWaiting: Color,
    val indicatorError: Color,

    // Buttons
    val buttonPrimaryBg: Color,
    val buttonPrimaryText: Color,
    val buttonPrimaryHoverBg: Color,
    val buttonSecondaryBg: Color,
    val buttonSecondaryText: Color,
    val buttonSecondaryHoverBg: Color,
    val buttonDangerBg: Color,
    val buttonDangerText: Color,

    // Toolbar
    val toolbarControlBg: Color,
    val toolbarControlBorder: Color,
    val actionStop: Color,
    val actionRun: Color,

    // Agent brand (theme-invariant)
    val agentClaude: Color,
    val agentOpenCode: Color,
    val agentCodex: Color,
    val agentGemini: Color,
    val agentQwen: Color,
    val agentCodeSpeak: Color,

    // Overlay
    val hoverOverlay: Color,
    val dropTargetBg: Color,
)

// ── DSColor — static palettes and CompositionLocal accessor ───────────────────

/**
 * Provides access to the two built-in palettes ([dark], [light]) and re-exposes
 * the active [DSColors] instance for non-Composable code that must reference
 * colours statically (e.g. initial default values).
 *
 * **In Composables always prefer `LocalDSColors.current`** — it responds
 * correctly to theme changes at runtime.
 */
public object DSColor {

    /** Dark palette — exact match to macOS DesignSystem/DSColor.swift */
    public val dark: DSColors = DSColors(
        // Surfaces
        surfaceBase          = Color(0xFF1A1B1E),
        surfaceRaised        = Color(0xFF212225),
        surfaceOverlay       = Color(0xFF2A2B2F),
        surfaceTabBar        = Color(0xFF17181B),
        surfaceTabActive     = Color(0xFF1A1B1E),
        surfaceTabInactive   = Color(0xFF17181B),
        surfaceTabHover      = Color(0xFF1F2023),
        surfaceInput         = Color(0xFF16171A),
        surfaceSelection     = Color(0xFF264F78),
        surfaceToolbar       = Color(0xFF1C1C1E),
        // Text
        textPrimary          = Color(0xFFD4D4D8),
        textSecondary        = Color(0xFF8B8B93),
        textMuted            = Color(0xFF55565C),
        textInverse          = Color(0xFF1A1B1E),
        textDisabled         = Color(0xFF3B3C42),
        textGhost            = Color(0xFF3F4046),
        // Borders
        borderDefault        = Color(0xFF2E2F33),
        borderSubtle         = Color(0xFF252629),
        borderFocus          = Color(0xFF4A9EFF),
        // Accent
        accentPrimary        = Color(0xFF4A9EFF),
        accentPrimaryHover   = Color(0xFF5BABFF),
        accentSecondary      = Color(0xFF7C3AED),
        accentPrimarySubtle  = Color(0xFF152740),
        // Git
        gitModified          = Color(0xFFE2B93D),
        gitAdded             = Color(0xFF3FB950),
        gitDeleted           = Color(0xFFF85149),
        gitUntracked         = Color(0xFF8B8B93),
        gitConflicted        = Color(0xFFF09000),
        gitRenamed           = Color(0xFF58A6FF),
        // Diff
        diffAddedBg          = Color(0xFF143D1F),
        diffDeletedBg        = Color(0xFF3D1214),
        diffGutter           = Color(0xFF55565C),
        // Activity indicators
        indicatorIdle        = Color(0xFF6E7681),
        indicatorRunning     = Color(0xFF3FB950),
        indicatorWaiting     = Color(0xFFE2B93D),
        indicatorError       = Color(0xFFF85149),
        // Buttons
        buttonPrimaryBg      = Color(0xFF4A9EFF),
        buttonPrimaryText    = Color.White,
        buttonPrimaryHoverBg = Color(0xFF5BABFF),
        buttonSecondaryBg    = Color(0xFF2A2B2F),
        buttonSecondaryText  = Color(0xFFD4D4D8),
        buttonSecondaryHoverBg = Color(0xFF333438),
        buttonDangerBg       = Color(0xFF3D1214),
        buttonDangerText     = Color(0xFFF85149),
        // Toolbar
        toolbarControlBg     = Color(0xFF252629),
        toolbarControlBorder = Color(0xFF3C3F41),
        actionStop           = Color(0xFFF85149),
        actionRun            = Color(0xFF3FB950),
        // Agent brand
        agentClaude          = Color(0xFFCC7847),
        agentOpenCode        = Color(0xFF6189F2),
        agentCodex           = Color(0xFF10A37F),
        agentGemini          = Color(0xFF4285F4),
        agentQwen            = Color(0xFF6B3FA0),
        agentCodeSpeak       = Color(0xFFE85D29),
        // Overlay
        hoverOverlay         = Color(0x12FFFFFF),
        dropTargetBg         = Color(0x144A9EFF),
    )

    /** Light palette */
    public val light: DSColors = DSColors(
        // Surfaces
        surfaceBase          = Color(0xFFFAFAFA),
        surfaceRaised        = Color(0xFFFFFFFF),
        surfaceOverlay       = Color(0xFFF0F0F2),
        surfaceTabBar        = Color(0xFFEFEFF1),
        surfaceTabActive     = Color(0xFFFFFFFF),
        surfaceTabInactive   = Color(0xFFEFEFF1),
        surfaceTabHover      = Color(0xFFE8E8EA),
        surfaceInput         = Color(0xFFF5F5F7),
        surfaceSelection     = Color(0xFFB8D4F0),
        surfaceToolbar       = Color(0xFFF2F2F4),
        // Text
        textPrimary          = Color(0xFF1A1A1A),
        textSecondary        = Color(0xFF666666),
        textMuted            = Color(0xFF999999),
        textInverse          = Color(0xFFFFFFFF),
        textDisabled         = Color(0xFFCCCCCC),
        textGhost            = Color(0xFFDDDDDD),
        // Borders
        borderDefault        = Color(0xFFDDDDE0),
        borderSubtle         = Color(0xFFE8E8EB),
        borderFocus          = Color(0xFF4A9EFF),
        // Accent — same as dark for brand consistency
        accentPrimary        = Color(0xFF4A9EFF),
        accentPrimaryHover   = Color(0xFF5BABFF),
        accentSecondary      = Color(0xFF7C3AED),
        accentPrimarySubtle  = Color(0xFFD6E8FF),
        // Git — same semantic colors
        gitModified          = Color(0xFFB08000),
        gitAdded             = Color(0xFF1A7F37),
        gitDeleted           = Color(0xFFCF222E),
        gitUntracked         = Color(0xFF666666),
        gitConflicted        = Color(0xFFBC4C00),
        gitRenamed           = Color(0xFF0969DA),
        // Diff
        diffAddedBg          = Color(0xFFDCFFE4),
        diffDeletedBg        = Color(0xFFFFEBE9),
        diffGutter           = Color(0xFF999999),
        // Activity indicators
        indicatorIdle        = Color(0xFF57606A),
        indicatorRunning     = Color(0xFF1A7F37),
        indicatorWaiting     = Color(0xFFB08000),
        indicatorError       = Color(0xFFCF222E),
        // Buttons
        buttonPrimaryBg      = Color(0xFF4A9EFF),
        buttonPrimaryText    = Color.White,
        buttonPrimaryHoverBg = Color(0xFF5BABFF),
        buttonSecondaryBg    = Color(0xFFEEEEF0),
        buttonSecondaryText  = Color(0xFF1A1A1A),
        buttonSecondaryHoverBg = Color(0xFFE4E4E6),
        buttonDangerBg       = Color(0xFFFFEBE9),
        buttonDangerText     = Color(0xFFCF222E),
        // Toolbar
        toolbarControlBg     = Color(0xFFE8E8EB),
        toolbarControlBorder = Color(0xFFCCCCCF),
        actionStop           = Color(0xFFCF222E),
        actionRun            = Color(0xFF1A7F37),
        // Agent brand — theme-invariant
        agentClaude          = Color(0xFFCC7847),
        agentOpenCode        = Color(0xFF6189F2),
        agentCodex           = Color(0xFF10A37F),
        agentGemini          = Color(0xFF4285F4),
        agentQwen            = Color(0xFF6B3FA0),
        agentCodeSpeak       = Color(0xFFE85D29),
        // Overlay
        hoverOverlay         = Color(0x0C000000),
        dropTargetBg         = Color(0x1A4A9EFF),
    )

}

// ── CompositionLocal ──────────────────────────────────────────────────────────

/**
 * CompositionLocal that carries the active [DSColors] palette down the tree.
 *
 * Provided by [VibeStudioTheme]. Access via `LocalDSColors.current` in any
 * Composable that needs design-system color tokens.
 */
public val LocalDSColors = staticCompositionLocalOf<DSColors> {
    error("LocalDSColors accessed outside of VibeStudioTheme")
}

// ── DSSpacing — 4pt grid ─────────────────────────────────────────────────────

public object DSSpacing {
    val xxs = 2.dp
    val xs  = 4.dp
    val sm  = 8.dp
    val md  = 12.dp
    val lg  = 16.dp
    val xl  = 20.dp
    val xxl = 24.dp
}

// ── DSRadius ─────────────────────────────────────────────────────────────────

public object DSRadius {
    val sm = 4.dp
    val md = 6.dp
    val lg = 8.dp
}

// ── DSLayout — exact match to macOS DSLayout.swift ───────────────────────────

public object DSLayout {
    // Toolbar
    val toolbarHeight           = 26.dp
    val toolbarButtonHeight     = 22.dp
    val toolbarIconButtonWidth  = 26.dp
    /** Width of macOS traffic lights (close/minimize/zoom) — standard Sonoma/Sequoia value. */
    val trafficLightsEnd        = 84.dp

    // Tab bar
    val tabBarHeight            = 36.dp
    val tabHeight               = 28.dp
    val tabMinWidth             = 120.dp
    val tabMaxWidth             = 200.dp
    val tabHorizontalPadding    = 12.dp
    val tabGap                  = 2.dp
    val tabCloseSize            = 16.dp
    val tabCloseIconSize        = 9.dp
    val tabAddButtonSize        = 28.dp
    val tabActiveIndicatorHeight = 2.dp

    // Sidebar
    val sidebarDefaultWidth     = 240.dp
    val sidebarMinWidth         = 180.dp
    val sidebarMaxWidth         = 400.dp
    val sidebarHorizontalPadding = 12.dp
    val iconStripWidth          = 44.dp
    val iconStripButtonSize     = 28.dp
    val sidebarActionButtonSize = 20.dp
    val chevronFrameWidth       = 14.dp

    // File tree
    val treeRowHeight           = 28.dp
    val treeIndent              = 16.dp
    val treeBaseIndent          = 4.dp

    // Git
    val gitSectionHeaderHeight  = 28.dp
    val gitFileRowHeight        = 28.dp
    val gitButtonHeight         = 28.dp
    val commitInputMinHeight    = 60.dp
    val commitInputMaxHeight    = 120.dp

    // Terminal
    val terminalPaddingTop      = 4.dp
    val terminalPaddingLeading  = 8.dp
    val terminalPaddingBottom   = 4.dp
    val terminalPaddingTrailing = 8.dp

    // Activity indicator
    val indicatorSize           = 6.dp

    // Changes panel
    val changesPanelDefaultWidth = 280.dp
    val changesPanelMinWidth    = 220.dp
    val changesPanelMaxWidth    = 450.dp
    val changesFileRowHeight    = 26.dp
    val statusLetterWidth       = 16.dp

    // Welcome
    val welcomeListMaxWidth     = 420.dp

    // Window
    val windowDefaultWidth      = 2100.dp
    val windowDefaultHeight     = 1312.dp
    val windowMinWidth          = 640.dp
    val windowMinHeight         = 400.dp
}

// ── DSFont — match macOS DSFont.swift sizes/weights ──────────────────────────

public object DSFont {
    val tabTitle         = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val tabBranch        = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Normal)
    val sidebarSection   = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    val sidebarItem      = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal)
    val sidebarItemSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal)
    val gitStatus        = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
    val gitBranch        = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)
    val gitAheadBehind   = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal)
    val buttonLabel      = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val commitInput      = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal)
    val tooltip          = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal)
    val smallButtonLabel = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
    val monoPath         = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, fontFamily = FontFamily.Monospace)
    val monoSmall        = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal, fontFamily = FontFamily.Monospace)
    val bodySmall        = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal)
    val bodyMedium       = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal)
    val statusBadge      = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    val badgeSmall       = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    val welcomeTitle     = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    val settingsTitle    = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

    // Icon sizes (used as font sizes for icon composables)
    val iconXS   = 8.sp
    val iconSM   = 9.sp
    val iconMD   = 10.sp
    val iconBase = 11.sp
    val iconLG   = 14.sp
}

// ── Material3 color schemes (mapped from DS tokens) ───────────────────────────

private fun buildDarkColorScheme(c: DSColors) = darkColorScheme(
    background          = c.surfaceBase,
    surface             = c.surfaceRaised,
    surfaceVariant      = c.surfaceOverlay,
    surfaceContainer    = c.surfaceOverlay,
    primary             = c.accentPrimary,
    onPrimary           = Color.White,
    primaryContainer    = c.accentPrimarySubtle,
    onPrimaryContainer  = c.textPrimary,
    secondary           = c.accentSecondary,
    onSecondary         = Color.White,
    error               = c.gitDeleted,
    onError             = Color.White,
    onBackground        = c.textPrimary,
    onSurface           = c.textPrimary,
    onSurfaceVariant    = c.textSecondary,
    outline             = c.borderDefault,
    outlineVariant      = c.borderSubtle,
    inverseSurface      = c.textPrimary,
    inverseOnSurface    = c.surfaceBase,
    scrim               = Color(0x99000000),
)

private fun buildLightColorScheme(c: DSColors) = lightColorScheme(
    background          = c.surfaceBase,
    surface             = c.surfaceRaised,
    surfaceVariant      = c.surfaceOverlay,
    surfaceContainer    = c.surfaceOverlay,
    primary             = c.accentPrimary,
    onPrimary           = Color.White,
    primaryContainer    = c.accentPrimarySubtle,
    onPrimaryContainer  = c.textPrimary,
    secondary           = c.accentSecondary,
    onSecondary         = Color.White,
    error               = c.gitDeleted,
    onError             = Color.White,
    onBackground        = c.textPrimary,
    onSurface           = c.textPrimary,
    onSurfaceVariant    = c.textSecondary,
    outline             = c.borderDefault,
    outlineVariant      = c.borderSubtle,
    inverseSurface      = c.textPrimary,
    inverseOnSurface    = c.surfaceBase,
    scrim               = Color(0x33000000),
)

// ── VibeStudioTheme ───────────────────────────────────────────────────────────

/**
 * Root theme composable that wires together [MaterialTheme] and [LocalDSColors].
 *
 * @param isDark When `true` the dark palette is applied; when `false` the light
 *   palette is used. Derive this value from [AppTheme] preference in [Main.kt].
 * @param content The Composable subtree that should receive the theme.
 */
@Composable
public fun VibeStudioTheme(
    isDark: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dsColors = if (isDark) DSColor.dark else DSColor.light
    val materialColors = if (isDark) {
        buildDarkColorScheme(dsColors)
    } else {
        buildLightColorScheme(dsColors)
    }

    CompositionLocalProvider(LocalDSColors provides dsColors) {
        MaterialTheme(
            colorScheme = materialColors,
            content = content,
        )
    }
}

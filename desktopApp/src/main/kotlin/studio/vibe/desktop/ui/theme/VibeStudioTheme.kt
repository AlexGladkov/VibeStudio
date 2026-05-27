package studio.vibe.desktop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── DSColor — exact match to macOS DesignSystem/DSColor.swift ────────────────

object DSColor {
    // Surfaces
    val surfaceBase       = Color(0xFF1A1B1E)
    val surfaceRaised     = Color(0xFF212225)
    val surfaceOverlay    = Color(0xFF2A2B2F)
    val surfaceTabBar     = Color(0xFF17181B)
    val surfaceTabActive  = Color(0xFF1A1B1E)
    val surfaceTabInactive = Color(0xFF17181B)
    val surfaceTabHover   = Color(0xFF1F2023)
    val surfaceInput      = Color(0xFF16171A)
    val surfaceSelection  = Color(0xFF264F78)
    val surfaceToolbar    = Color(0xFF1C1C1E)

    // Text
    val textPrimary       = Color(0xFFD4D4D8)
    val textSecondary     = Color(0xFF8B8B93)
    val textMuted         = Color(0xFF55565C)
    val textInverse       = Color(0xFF1A1B1E)
    val textDisabled      = Color(0xFF3B3C42)
    val textGhost         = Color(0xFF3F4046)

    // Borders
    val borderDefault     = Color(0xFF2E2F33)
    val borderSubtle      = Color(0xFF252629)
    val borderFocus       = Color(0xFF4A9EFF)

    // Accent
    val accentPrimary     = Color(0xFF4A9EFF)
    val accentPrimaryHover = Color(0xFF5BABFF)
    val accentSecondary   = Color(0xFF7C3AED)
    val accentPrimarySubtle = Color(0xFF152740)

    // Git
    val gitModified       = Color(0xFFE2B93D)
    val gitAdded          = Color(0xFF3FB950)
    val gitDeleted        = Color(0xFFF85149)
    val gitUntracked      = Color(0xFF8B8B93)
    val gitConflicted     = Color(0xFFF09000)
    val gitRenamed        = Color(0xFF58A6FF)

    // Diff
    val diffAddedBg       = Color(0xFF143D1F)
    val diffDeletedBg     = Color(0xFF3D1214)
    val diffGutter        = Color(0xFF55565C)

    // Activity indicators
    val indicatorIdle     = Color(0xFF6E7681)
    val indicatorRunning  = Color(0xFF3FB950)
    val indicatorWaiting  = Color(0xFFE2B93D)
    val indicatorError    = Color(0xFFF85149)

    // Buttons
    val buttonPrimaryBg   = Color(0xFF4A9EFF)
    val buttonPrimaryText = Color.White
    val buttonPrimaryHoverBg = Color(0xFF5BABFF)
    val buttonSecondaryBg = Color(0xFF2A2B2F)
    val buttonSecondaryText = Color(0xFFD4D4D8)
    val buttonSecondaryHoverBg = Color(0xFF333438)
    val buttonDangerBg    = Color(0xFF3D1214)
    val buttonDangerText  = Color(0xFFF85149)

    // Toolbar
    val toolbarControlBg  = Color(0xFF252629)
    val toolbarControlBorder = Color(0xFF3C3F41)
    val actionStop        = Color(0xFFF85149)
    val actionRun         = Color(0xFF3FB950)

    // Agent brand
    val agentClaude       = Color(0xFFCC7847)
    val agentOpenCode     = Color(0xFF6189F2)
    val agentCodex        = Color(0xFF10A37F)
    val agentGemini       = Color(0xFF4285F4)
    val agentQwen         = Color(0xFF6B3FA0)
    val agentCodeSpeak    = Color(0xFFE85D29)

    // Overlay
    val hoverOverlay      = Color(0x12FFFFFF) // 7% white
    val dropTargetBg      = Color(0x144A9EFF) // 8% accent
}

// ── DSSpacing — 4pt grid ─────────────────────────────────────────────────────

object DSSpacing {
    val xxs = 2.dp
    val xs  = 4.dp
    val sm  = 8.dp
    val md  = 12.dp
    val lg  = 16.dp
    val xl  = 20.dp
    val xxl = 24.dp
}

// ── DSRadius ─────────────────────────────────────────────────────────────────

object DSRadius {
    val sm = 4.dp
    val md = 6.dp
    val lg = 8.dp
}

// ── DSLayout — exact match to macOS DSLayout.swift ───────────────────────────

object DSLayout {
    // Toolbar
    val toolbarHeight           = 26.dp
    val toolbarButtonHeight     = 22.dp
    val toolbarIconButtonWidth  = 26.dp

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
    val iconStripWidth          = 32.dp
    val iconStripButtonSize     = 24.dp
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

    // Window
    val windowDefaultWidth      = 2100.dp
    val windowDefaultHeight     = 1312.dp
    val windowMinWidth          = 640.dp
    val windowMinHeight         = 400.dp
}

// ── DSFont — match macOS DSFont.swift sizes/weights ──────────────────────────

object DSFont {
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
    val iconXS  = 8.sp
    val iconSM  = 9.sp
    val iconMD  = 10.sp
    val iconBase = 11.sp
    val iconLG  = 14.sp
}

// ── Material3 color scheme (mapped from DS tokens) ───────────────────────────

private val VibeStudioDarkColors = darkColorScheme(
    background          = DSColor.surfaceBase,
    surface             = DSColor.surfaceRaised,
    surfaceVariant      = DSColor.surfaceOverlay,
    surfaceContainer    = DSColor.surfaceOverlay,
    primary             = DSColor.accentPrimary,
    onPrimary           = Color.White,
    primaryContainer    = DSColor.accentPrimarySubtle,
    onPrimaryContainer  = DSColor.textPrimary,
    secondary           = DSColor.accentSecondary,
    onSecondary         = Color.White,
    error               = DSColor.gitDeleted,
    onError             = Color.White,
    onBackground        = DSColor.textPrimary,
    onSurface           = DSColor.textPrimary,
    onSurfaceVariant    = DSColor.textSecondary,
    outline             = DSColor.borderDefault,
    outlineVariant      = DSColor.borderSubtle,
    inverseSurface      = DSColor.textPrimary,
    inverseOnSurface    = DSColor.surfaceBase,
    scrim               = Color(0x99000000),
)

@Composable
fun VibeStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VibeStudioDarkColors,
        content = content,
    )
}

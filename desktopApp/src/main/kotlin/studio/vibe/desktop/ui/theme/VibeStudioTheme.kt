package studio.vibe.desktop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── VibeStudio color palette ──────────────────────────────────────────────────
// Mirrors the macOS app aesthetic: VS Code-inspired dark with blue accent.

internal val ColorBackground = Color(0xFF1E1E1E)       // Main editor/terminal background
internal val ColorSurface = Color(0xFF252526)           // Sidebar / panel background
internal val ColorSurfaceVariant = Color(0xFF2D2D30)    // Elevated surfaces, dropdowns
internal val ColorSurfaceContainer = Color(0xFF333333)  // Hover states, selected rows
internal val ColorAccentBlue = Color(0xFF569CD6)        // Primary accent (links, active tabs)
internal val ColorAccentTeal = Color(0xFF4EC9B0)        // Secondary accent (types, keywords)
internal val ColorAccentGreen = Color(0xFF6A9955)       // Success, additions in diff
internal val ColorAccentRed = Color(0xFFF44747)         // Error, deletions in diff
internal val ColorAccentYellow = Color(0xFFDCDCAA)      // Warnings, functions
internal val ColorTextPrimary = Color(0xFFD4D4D4)       // Main text
internal val ColorTextSecondary = Color(0xFF808080)     // Muted / placeholder text
internal val ColorBorder = Color(0xFF3C3C3C)            // Dividers and outlines
internal val ColorScrollbar = Color(0xFF424242)         // Scrollbar thumb

private val VibeStudioDarkColors = darkColorScheme(
    background = ColorBackground,
    surface = ColorSurface,
    surfaceVariant = ColorSurfaceVariant,
    surfaceContainer = ColorSurfaceContainer,
    primary = ColorAccentBlue,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1A3A5C),
    onPrimaryContainer = ColorTextPrimary,
    secondary = ColorAccentTeal,
    onSecondary = Color(0xFF003733),
    secondaryContainer = Color(0xFF1A3F3C),
    onSecondaryContainer = ColorTextPrimary,
    tertiary = ColorAccentYellow,
    onTertiary = Color(0xFF2B2B00),
    error = ColorAccentRed,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF3F0000),
    onErrorContainer = Color(0xFFFFB4A2),
    onBackground = ColorTextPrimary,
    onSurface = ColorTextPrimary,
    onSurfaceVariant = ColorTextSecondary,
    outline = ColorBorder,
    outlineVariant = Color(0xFF2A2A2A),
    inverseSurface = Color(0xFFD4D4D4),
    inverseOnSurface = Color(0xFF1E1E1E),
    inversePrimary = Color(0xFF004B8D),
    scrim = Color(0x99000000),
)

@Composable
fun VibeStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VibeStudioDarkColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}

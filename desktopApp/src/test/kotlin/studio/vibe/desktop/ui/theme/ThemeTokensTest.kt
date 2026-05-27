package studio.vibe.desktop.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Unit tests for the design-system token objects in [VibeStudioTheme.kt].
 *
 * Invariants checked:
 *  - Every [DSColor] value has alpha > 0 (no accidentally transparent tokens).
 *  - [DSFont] styles that are expected to use [FontFamily.Monospace] do so.
 *  - Every [DSSpacing] value is strictly positive.
 *  - Every [DSLayout] dimension value is strictly positive.
 */
class ThemeTokensTest {

    // ── DSColor ───────────────────────────────────────────────────────────────

    @Test
    fun dsColor_surfaceBase_isNonTransparent() {
        assertTrue(DSColor.surfaceBase.alpha > 0f, "surfaceBase must be non-transparent")
    }

    @Test
    fun dsColor_surfaceRaised_isNonTransparent() {
        assertTrue(DSColor.surfaceRaised.alpha > 0f)
    }

    @Test
    fun dsColor_textPrimary_isNonTransparent() {
        assertTrue(DSColor.textPrimary.alpha > 0f)
    }

    @Test
    fun dsColor_accentPrimary_isNonTransparent() {
        assertTrue(DSColor.accentPrimary.alpha > 0f)
    }

    @Test
    fun dsColor_gitModified_isNonTransparent() {
        assertTrue(DSColor.gitModified.alpha > 0f)
    }

    @Test
    fun dsColor_gitAdded_isNonTransparent() {
        assertTrue(DSColor.gitAdded.alpha > 0f)
    }

    @Test
    fun dsColor_gitDeleted_isNonTransparent() {
        assertTrue(DSColor.gitDeleted.alpha > 0f)
    }

    @Test
    fun dsColor_indicatorRunning_isNonTransparent() {
        assertTrue(DSColor.indicatorRunning.alpha > 0f)
    }

    @Test
    fun dsColor_buttonPrimaryBg_isNonTransparent() {
        assertTrue(DSColor.buttonPrimaryBg.alpha > 0f)
    }

    @Test
    fun dsColor_actionRun_isNonTransparent() {
        assertTrue(DSColor.actionRun.alpha > 0f)
    }

    @Test
    fun dsColor_actionStop_isNonTransparent() {
        assertTrue(DSColor.actionStop.alpha > 0f)
    }

    // Overlay tokens intentionally have partial alpha — but alpha must still be > 0.
    @Test
    fun dsColor_hoverOverlay_hasNonZeroAlpha() {
        assertTrue(DSColor.hoverOverlay.alpha > 0f, "hoverOverlay must have non-zero alpha")
    }

    @Test
    fun dsColor_dropTargetBg_hasNonZeroAlpha() {
        assertTrue(DSColor.dropTargetBg.alpha > 0f, "dropTargetBg must have non-zero alpha")
    }

    // ── DSFont ────────────────────────────────────────────────────────────────

    @Test
    fun dsFont_gitStatus_usesMonospaceFamily() {
        assertTrue(
            DSFont.gitStatus.fontFamily == FontFamily.Monospace,
            "gitStatus must use FontFamily.Monospace",
        )
    }

    @Test
    fun dsFont_monoPath_usesMonospaceFamily() {
        assertTrue(
            DSFont.monoPath.fontFamily == FontFamily.Monospace,
            "monoPath must use FontFamily.Monospace",
        )
    }

    @Test
    fun dsFont_monoSmall_usesMonospaceFamily() {
        assertTrue(
            DSFont.monoSmall.fontFamily == FontFamily.Monospace,
            "monoSmall must use FontFamily.Monospace",
        )
    }

    @Test
    fun dsFont_tabTitle_hasMediumWeight() {
        assertTrue(
            DSFont.tabTitle.fontWeight == FontWeight.Medium,
            "tabTitle must have FontWeight.Medium",
        )
    }

    @Test
    fun dsFont_sidebarSection_hasSemiBoldWeight() {
        assertTrue(
            DSFont.sidebarSection.fontWeight == FontWeight.SemiBold,
            "sidebarSection must have FontWeight.SemiBold",
        )
    }

    @Test
    fun dsFont_welcomeTitle_hasPositiveFontSize() {
        val size = DSFont.welcomeTitle.fontSize.value
        assertTrue(size > 0f, "welcomeTitle fontSize must be positive, got $size")
    }

    // ── DSSpacing ─────────────────────────────────────────────────────────────

    @Test
    fun dsSpacing_xxs_isPositive() {
        assertTrue(DSSpacing.xxs.value > 0f, "DSSpacing.xxs must be positive")
    }

    @Test
    fun dsSpacing_xs_isPositive() {
        assertTrue(DSSpacing.xs.value > 0f)
    }

    @Test
    fun dsSpacing_sm_isPositive() {
        assertTrue(DSSpacing.sm.value > 0f)
    }

    @Test
    fun dsSpacing_md_isPositive() {
        assertTrue(DSSpacing.md.value > 0f)
    }

    @Test
    fun dsSpacing_lg_isPositive() {
        assertTrue(DSSpacing.lg.value > 0f)
    }

    @Test
    fun dsSpacing_xl_isPositive() {
        assertTrue(DSSpacing.xl.value > 0f)
    }

    @Test
    fun dsSpacing_xxl_isPositive() {
        assertTrue(DSSpacing.xxl.value > 0f)
    }

    @Test
    fun dsSpacing_valuesAreStrictlyIncreasing() {
        val values = listOf(
            DSSpacing.xxs.value,
            DSSpacing.xs.value,
            DSSpacing.sm.value,
            DSSpacing.md.value,
            DSSpacing.lg.value,
            DSSpacing.xl.value,
            DSSpacing.xxl.value,
        )
        for (i in 1 until values.size) {
            assertTrue(
                values[i] > values[i - 1],
                "DSSpacing values must be strictly increasing at index $i: ${values[i - 1]} >= ${values[i]}",
            )
        }
    }

    // ── DSLayout ──────────────────────────────────────────────────────────────

    @Test
    fun dsLayout_toolbarHeight_isPositive() {
        assertTrue(DSLayout.toolbarHeight.value > 0f)
    }

    @Test
    fun dsLayout_tabBarHeight_isPositive() {
        assertTrue(DSLayout.tabBarHeight.value > 0f)
    }

    @Test
    fun dsLayout_sidebarDefaultWidth_isPositive() {
        assertTrue(DSLayout.sidebarDefaultWidth.value > 0f)
    }

    @Test
    fun dsLayout_sidebarMinWidth_lessThanDefaultWidth() {
        assertTrue(
            DSLayout.sidebarMinWidth.value < DSLayout.sidebarDefaultWidth.value,
            "sidebarMinWidth must be less than sidebarDefaultWidth",
        )
    }

    @Test
    fun dsLayout_sidebarMaxWidth_greaterThanDefaultWidth() {
        assertTrue(
            DSLayout.sidebarMaxWidth.value > DSLayout.sidebarDefaultWidth.value,
            "sidebarMaxWidth must be greater than sidebarDefaultWidth",
        )
    }

    @Test
    fun dsLayout_windowMinWidth_isPositive() {
        assertTrue(DSLayout.windowMinWidth.value > 0f)
    }

    @Test
    fun dsLayout_windowMinHeight_isPositive() {
        assertTrue(DSLayout.windowMinHeight.value > 0f)
    }
}

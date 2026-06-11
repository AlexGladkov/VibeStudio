package studio.vibe.shared.core.common

import studio.vibe.shared.coordinator.AppMode
import studio.vibe.shared.coordinator.AppNavigationCoordinator
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [AppNavigationCoordinator].
 *
 * Covers:
 * - Initial state of all StateFlow properties
 * - syncMode transitions (Regular↔CodeSpeak)
 * - syncMode same-mode no-op
 * - syncMode panel reset behaviour on entering CodeSpeak
 * - syncMode CodeSpeak→Regular does NOT reset panels (actual code behaviour)
 * - toggleSidebar flips the flag
 */
class AppNavigationCoordinatorTest {

    private lateinit var coordinator: AppNavigationCoordinator

    @BeforeTest
    fun setup() {
        coordinator = AppNavigationCoordinator()
    }

    // -------------------------------------------------------------------------
    // Initial values
    // -------------------------------------------------------------------------

    @Test
    fun initialState_currentMode_isRegular() {
        assertEquals(AppMode.Regular, coordinator.currentMode.value)
    }

    @Test
    fun initialState_showingSidebar_isTrue() {
        assertTrue(coordinator.showingSidebar.value)
    }

    @Test
    fun initialState_showingSettings_isFalse() {
        assertFalse(coordinator.showingSettings.value)
    }

    @Test
    fun initialState_showingChangesPanel_isFalse() {
        assertFalse(coordinator.showingChangesPanel.value)
    }

    @Test
    fun initialState_showingSpecPanel_isFalse() {
        assertFalse(coordinator.showingSpecPanel.value)
    }

    @Test
    fun initialState_showingTraceabilityPanel_isFalse() {
        assertFalse(coordinator.showingTraceabilityPanel.value)
    }

    @Test
    fun initialState_agentToInstall_isNull() {
        assertEquals(null, coordinator.agentToInstall.value)
    }

    @Test
    fun initialState_codeSpeakBuildRequested_isFalse() {
        assertFalse(coordinator.codeSpeakBuildRequested.value)
    }

    // -------------------------------------------------------------------------
    // syncMode — transitions
    // -------------------------------------------------------------------------

    @Test
    fun syncMode_isCodeSpeakTrue_setsCurrentModeToCodeSpeak() {
        // Arrange: coordinator starts in Regular mode

        // Act
        coordinator.syncMode(isCodeSpeak = true)

        // Assert
        assertEquals(AppMode.CodeSpeak, coordinator.currentMode.value)
    }

    @Test
    fun syncMode_isCodeSpeakFalse_whenInCodeSpeakMode_setsCurrentModeToRegular() {
        // Arrange: put coordinator into CodeSpeak mode first
        coordinator.syncMode(isCodeSpeak = true)
        assertEquals(AppMode.CodeSpeak, coordinator.currentMode.value)

        // Act
        coordinator.syncMode(isCodeSpeak = false)

        // Assert
        assertEquals(AppMode.Regular, coordinator.currentMode.value)
    }

    // -------------------------------------------------------------------------
    // syncMode — no-op when same mode
    // -------------------------------------------------------------------------

    @Test
    fun syncMode_sameMode_regular_doesNotResetPanels() {
        // Arrange: open some panels while in Regular mode
        coordinator.setShowingChangesPanel(true)
        coordinator.setShowingSpecPanel(true)

        // Act: call syncMode with the same mode (Regular → Regular)
        coordinator.syncMode(isCodeSpeak = false)

        // Assert: panels are untouched — syncMode returned early
        assertTrue(coordinator.showingChangesPanel.value)
        assertTrue(coordinator.showingSpecPanel.value)
        assertEquals(AppMode.Regular, coordinator.currentMode.value)
    }

    @Test
    fun syncMode_sameMode_codeSpeak_doesNotResetPanels() {
        // Arrange: enter CodeSpeak, then manually open spec panel (allowed by the app)
        coordinator.syncMode(isCodeSpeak = true)
        coordinator.setShowingSpecPanel(true)

        // Act: call syncMode again with the same mode (CodeSpeak → CodeSpeak)
        coordinator.syncMode(isCodeSpeak = true)

        // Assert: no reset happened because newMode == currentMode
        assertTrue(coordinator.showingSpecPanel.value)
    }

    // -------------------------------------------------------------------------
    // syncMode — entering CodeSpeak resets right-side panels
    // -------------------------------------------------------------------------

    @Test
    fun syncMode_regularToCodeSpeak_resetsChangesPanelToFalse() {
        // Arrange: open the changes panel while in Regular mode
        coordinator.setShowingChangesPanel(true)
        assertTrue(coordinator.showingChangesPanel.value)

        // Act
        coordinator.syncMode(isCodeSpeak = true)

        // Assert
        assertFalse(coordinator.showingChangesPanel.value)
    }

    @Test
    fun syncMode_regularToCodeSpeak_resetsSpecPanelToFalse() {
        // Arrange
        coordinator.setShowingSpecPanel(true)

        // Act
        coordinator.syncMode(isCodeSpeak = true)

        // Assert
        assertFalse(coordinator.showingSpecPanel.value)
    }

    @Test
    fun syncMode_regularToCodeSpeak_resetsTraceabilityPanelToFalse() {
        // Arrange
        coordinator.setShowingTraceabilityPanel(true)

        // Act
        coordinator.syncMode(isCodeSpeak = true)

        // Assert
        assertFalse(coordinator.showingTraceabilityPanel.value)
    }

    // -------------------------------------------------------------------------
    // syncMode — CodeSpeak→Regular does NOT reset panels (actual implementation)
    //
    // The current syncMode implementation only resets panels when entering
    // CodeSpeak mode (newMode == AppMode.CodeSpeak branch).
    // There is no symmetric reset on the Regular side.
    // These tests document and verify the actual behaviour.
    // -------------------------------------------------------------------------

    @Test
    fun syncMode_codeSpeakToRegular_doesNotResetSpecPanel() {
        // Arrange: enter CodeSpeak, then open spec panel
        coordinator.syncMode(isCodeSpeak = true)
        coordinator.setShowingSpecPanel(true)

        // Act: switch back to Regular
        coordinator.syncMode(isCodeSpeak = false)

        // Assert: spec panel remains open — no reset on Regular transition
        assertTrue(coordinator.showingSpecPanel.value)
    }

    @Test
    fun syncMode_codeSpeakToRegular_doesNotResetTraceabilityPanel() {
        // Arrange
        coordinator.syncMode(isCodeSpeak = true)
        coordinator.setShowingTraceabilityPanel(true)

        // Act
        coordinator.syncMode(isCodeSpeak = false)

        // Assert
        assertTrue(coordinator.showingTraceabilityPanel.value)
    }

    // -------------------------------------------------------------------------
    // toggleSidebar
    // -------------------------------------------------------------------------

    @Test
    fun toggleSidebar_whenTrue_setsFalse() {
        // Arrange: sidebar is visible by default
        assertTrue(coordinator.showingSidebar.value)

        // Act
        coordinator.toggleSidebar()

        // Assert
        assertFalse(coordinator.showingSidebar.value)
    }

    @Test
    fun toggleSidebar_whenFalse_setsTrue() {
        // Arrange
        coordinator.setShowingSidebar(false)
        assertFalse(coordinator.showingSidebar.value)

        // Act
        coordinator.toggleSidebar()

        // Assert
        assertTrue(coordinator.showingSidebar.value)
    }

    @Test
    fun toggleSidebar_calledTwice_returnsToOriginalState() {
        // Arrange
        val original = coordinator.showingSidebar.value

        // Act
        coordinator.toggleSidebar()
        coordinator.toggleSidebar()

        // Assert
        assertEquals(original, coordinator.showingSidebar.value)
    }
}

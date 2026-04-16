// MARK: - TCCConsentCoordinator
// Responsible for acquiring TCC (Transparency, Consent, and Control) permissions.
// macOS 14+, Swift 5.10

import Foundation
import OSLog

/// Acquires TCC file-system consent and marks the app as ready.
///
/// Extracted from `AppLifecycleCoordinator` to respect SRP — consent acquisition
/// is a distinct responsibility from session management and project observation.
@MainActor
final class TCCConsentCoordinator {

    private let appReadyState: AppReadyState
    private let agentAvailability: any AgentAvailabilityChecking

    init(appReadyState: AppReadyState, agentAvailability: any AgentAvailabilityChecking) {
        self.appReadyState = appReadyState
        self.agentAvailability = agentAvailability
    }

    /// Probe `~/Documents` on a background thread to trigger the TCC consent dialog.
    ///
    /// Returns AFTER TCC resolves (granted or denied) but BEFORE revealing the UI.
    /// The caller is responsible for calling `revealUI()` at the right time.
    ///
    /// The background thread BLOCKS on the kernel-level TCC check while the main
    /// run loop stays live to present the consent dialog.
    func probeForConsent() async {
        await Task.detached(priority: .userInitiated) {
            let documentsURL = FileManager.default.urls(
                for: .documentDirectory, in: .userDomainMask
            ).first!
            _ = try? FileManager.default.contentsOfDirectory(
                at: documentsURL, includingPropertiesForKeys: nil
            )
        }.value
        Logger.app.info("TCCConsentCoordinator: TCC probe completed")
    }

    /// Open the TCC gate so RootView renders the full UI, and kick off agent
    /// availability refresh now that PATH is fully inherited from the TCC-granted
    /// process environment.
    ///
    /// Must be called on MainActor after `probeForConsent()` returns AND after
    /// `projectObserver.start()` has set the correct `currentMode` — so the UI
    /// opens directly in CodeSpeak (or Regular) mode without a visible flash.
    func revealUI() {
        appReadyState.markTCCGranted()
        Logger.app.info("TCCConsentCoordinator: UI revealed (TCC gate opened)")
        agentAvailability.refreshAll()
    }
}

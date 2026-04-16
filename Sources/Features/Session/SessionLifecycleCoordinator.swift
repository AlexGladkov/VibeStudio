// MARK: - SessionLifecycleCoordinator
// Responsible for restoring and saving terminal sessions.
// macOS 14+, Swift 5.10

import Foundation
import OSLog

/// Manages session restore/save and initial project activation.
///
/// Extracted from `AppLifecycleCoordinator` to respect SRP — session lifecycle
/// is a distinct responsibility from TCC consent and project observation.
@MainActor
final class SessionLifecycleCoordinator {

    private let saveSessionUseCase: SaveSessionUseCase
    private let restoreSessionUseCase: RestoreSessionUseCase
    private let activateFirstProjectUseCase: ActivateFirstProjectUseCase

    init(
        projectManager: any ProjectManaging,
        terminalManager: any TerminalSessionManaging,
        sessionPersistence: any SessionPersisting
    ) {
        self.saveSessionUseCase = SaveSessionUseCase(
            projectManager: projectManager,
            terminalManager: terminalManager,
            sessionPersistence: sessionPersistence
        )
        self.restoreSessionUseCase = RestoreSessionUseCase(
            projectManager: projectManager,
            terminalManager: terminalManager,
            sessionPersistence: sessionPersistence
        )
        self.activateFirstProjectUseCase = ActivateFirstProjectUseCase(
            projectManager: projectManager,
            terminalManager: terminalManager
        )
    }

    /// Restore the previous session from disk; activate first project if none restored.
    func restore() async {
        await restoreSessionUseCase.execute()
        // Fallback: activate the first project if none was restored (first launch
        // or all saved projects were missing from disk).
        activateFirstProjectUseCase.execute()
        Logger.app.info("SessionLifecycleCoordinator: session restored")
    }

    /// Save current session to disk before termination.
    func save() async {
        await saveSessionUseCase.execute()
        Logger.app.info("SessionLifecycleCoordinator: session saved")
    }
}

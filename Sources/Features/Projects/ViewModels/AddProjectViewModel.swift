// MARK: - AddProjectViewModel
// Presentation logic for opening recent projects.
// macOS 14+, Swift 5.10

import Foundation
import Observation
import OSLog

/// Manages the logic of opening recent projects from the popover and welcome screen.
@Observable
@MainActor
final class AddProjectViewModel {

    // MARK: - Output State

    private(set) var openError: String?

    // MARK: - Dependencies

    private let projectManager: any ProjectManaging

    // MARK: - Init

    init(projectManager: any ProjectManaging) {
        self.projectManager = projectManager
    }

    // MARK: - Actions

    /// Open a recent project. Returns true if the sheet should dismiss.
    func openRecentProject(_ project: Project) -> Bool {
        openError = nil
        do {
            let opened = try projectManager.addProject(at: project.path)
            projectManager.activeProjectId = opened.id
            return true
        } catch ProjectManagerError.duplicate(let existingId, _) {
            projectManager.activeProjectId = existingId
            return true
        } catch {
            openError = "Cannot open \"\(project.name)\": \(error.localizedDescription)"
            Logger.ui.error("AddProjectViewModel: failed to open recent project: \(error.localizedDescription, privacy: .public)")
            return false
        }
    }

    /// Open a project from file importer result.
    func openProject(at url: URL) {
        openError = nil
        do {
            let project = try projectManager.addProject(at: url)
            projectManager.activeProjectId = project.id
        } catch {
            Logger.ui.error("AddProjectViewModel: failed to add project: \(error.localizedDescription, privacy: .public)")
        }
    }

    func clearError() {
        openError = nil
    }
}

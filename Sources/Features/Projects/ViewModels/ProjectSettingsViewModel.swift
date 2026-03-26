// MARK: - ProjectSettingsViewModel
// Presentation logic for Project Settings sheet.
// macOS 14+, Swift 5.10

import Foundation
import Observation
import OSLog

/// Manages state and business logic for editing project settings.
@Observable
@MainActor
final class ProjectSettingsViewModel {

    // MARK: - Input State

    var productionURL: String = ""

    // MARK: - Dependencies

    private let projectManager: any ProjectManaging
    private let project: Project

    // MARK: - Init

    init(projectManager: any ProjectManaging, project: Project) {
        self.projectManager = projectManager
        self.project = project
        self.productionURL = project.productionURL ?? ""
    }

    // MARK: - Actions

    /// Save settings and return whether to dismiss the sheet.
    func saveSettings() -> Bool {
        var trimmed = productionURL.trimmingCharacters(in: .whitespacesAndNewlines)
        // Auto-prepend https:// if user forgot the scheme
        if !trimmed.isEmpty, !trimmed.hasPrefix("http://"), !trimmed.hasPrefix("https://") {
            trimmed = "https://" + trimmed
        }
        try? projectManager.updateProject(project.id) {
            $0.productionURL = trimmed.isEmpty ? nil : trimmed
        }
        return true
    }
}

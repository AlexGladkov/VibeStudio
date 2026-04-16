// MARK: - CreateProjectUseCase
// Use case for creating a new project directory and registering it.
// macOS 14+, Swift 5.10

import Foundation

/// Creates a new project directory on disk and registers it in the project manager.
///
/// Extracted from `CreateNewProjectSheet` to keep the view free of filesystem
/// operations — all business logic lives in the use case layer.
@MainActor
struct CreateProjectUseCase {

    let projectManager: any ProjectManaging

    /// Create a new project directory at `parent/name` and activate it.
    ///
    /// - Parameters:
    ///   - name: The folder name (already trimmed, non-empty).
    ///   - parent: Parent directory where the folder will be created.
    /// - Throws: `FileManager` error if creation fails, or `ProjectManagerError` on duplicate.
    @discardableResult
    func execute(name: String, parent: URL) throws -> Project {
        let newURL = parent.appendingPathComponent(name)
        try FileManager.default.createDirectory(
            at: newURL,
            withIntermediateDirectories: true,
            attributes: nil
        )
        let project = try projectManager.addProject(at: newURL)
        projectManager.activeProjectId = project.id
        return project
    }
}
